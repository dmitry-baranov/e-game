package ru.itis.diploma.experiment.service;

import ru.itis.diploma.experiment.config.ExperimentConfig;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.itis.diploma.game.dto.*;
import ru.itis.diploma.experiment.bot.BotDecisions;
import ru.itis.diploma.experiment.bot.BotParticipants;
import ru.itis.diploma.experiment.bot.BotPolicies;
import ru.itis.diploma.experiment.bot.policy.CreditGuard;
import ru.itis.diploma.experiment.model.ExperimentRun;
import ru.itis.diploma.experiment.model.ExperimentSeries;
import ru.itis.diploma.experiment.model.ExperimentPair;
import ru.itis.diploma.experiment.repository.ExperimentPairRepository;
import ru.itis.diploma.experiment.repository.ExperimentRunRepository;
import ru.itis.diploma.experiment.repository.ExperimentSeriesRepository;
import ru.itis.diploma.game.GameSimulation;
import ru.itis.diploma.game.model.*;
import ru.itis.diploma.game.model.enums.GameStatus;
import ru.itis.diploma.game.repository.*;
import ru.itis.diploma.game.service.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Проводит автоматические игры обычными игровыми сервисами. claim выдаёт попытку
 * под блокировкой серии; advance выполняет один игровой день в одной транзакции.
 * Боты внутри дня обрабатываются последовательно, но их снимки фиксируются
 * ДО применения любого плана: соперники не видят будущее решение друг друга.
 */
@Service @RequiredArgsConstructor
public class ExperimentEngine {
    private final TransactionTemplate transactions;
    private final ExperimentRunRepository runs;
    private final ExperimentSeriesRepository series;
    private final ExperimentPairRepository pairs;
    private final ExperimentScenarios scenarios;
    private final BotParticipants botParticipants;
    private final ManufacturerRepository manufacturers;
    private final GameRepository games;
    private final GameResultRepository results;
    private final GameSimulation gameSimulation;
    private final ManufacturerService manufacturerService;
    private final GameDayReadModel gameDayReadModel;
    private final CreditGuard creditGuard;
    private final StrategyTrainingCapture trainingCapture;
    private final BotDecisions botDecisions;
    private final ExperimentDecisionJournal journal;
    private final ProductionParametersRepository productions;

    /**
     * Выдача игры потоку. Под блокировкой серии проверяются цель и параллелизм:
     * сначала продолжаем QUEUED попытку с прежней игрой, затем при необходимости
     * создаём новую по сохранённой версии генератора. Завершённая оценочная игра
     * ждёт партнёра в WAITING_PAIR и не считается пригодной отдельно от него.
     */
    public Long claim(Long seriesId, ExperimentConfig config) {
        return transactions.execute(tx -> {
            var group = series.lockById(seriesId).orElseThrow();
            if (!"RUNNING".equals(group.getStatus())) return null;
            int target = group.getTarget();
            long done = runs.countBySeriesIdAndStatus(seriesId, "COMPLETED");
            long active = runs.countBySeriesIdAndStatus(seriesId, "RUNNING");
            long waiting = runs.countBySeriesIdAndStatus(seriesId, "WAITING_PAIR");
            if (done >= target) {
                if (active == 0) group.setStatus("DONE");
                return null;
            }
            if (active >= config.getParallel()) return null;
            var queued = runs.findFirstBySeriesIdAndStatusOrderByNumberDesc(seriesId, "QUEUED");
            if (queued.isPresent()) {
                queued.get().setStatus("RUNNING");
                return queued.get().getId();
            }
            if (done + active + waiting >= target) return null;
            if (group.getNextNumber() > config.getMaxAttempts()) {
                if (active == 0) group.setStatus("EXHAUSTED");
                return null;
            }
            return createRun(group, config).getId();
        });
    }

    private ExperimentRun createRun(ExperimentSeries group, ExperimentConfig config) {
        int number = group.getNextNumber();
        group.setNextNumber(number + 1);
        var scenario = scenarios.generate(config, number, group.getGeneratorVersion());
        var run = new ExperimentRun();
        run.setSeries(group);
        run.setNumber(number);
        if ("EVALUATE".equals(group.getMode())) {
            int pairNumber = (number + 1) / 2;
            var pair = pairs.findBySeriesIdAndPairNumber(group.getId(), pairNumber).orElseGet(() -> {
                var created = new ExperimentPair();
                created.setSeries(group);
                created.setPairNumber(pairNumber);
                created.setSeed(scenario.seed());
                created.setMarket(scenario.market());
                return pairs.save(created);
            });
            if (pair.getSeed() != scenario.seed() || !pair.getMarket().equals(scenario.market()))
                throw new IllegalStateException("Условия EVALUATE-пары изменились");
            run.setPair(pair);
            run.setSeed(pair.getSeed());
            run.setMarket(pair.getMarket());
        } else {
            run.setSeed(scenario.seed());
            run.setMarket(scenario.market());
        }
        run.setBots(scenario.bots());
        run.setHorizon(scenario.horizon());
        runs.save(run);
        run.setGame(botParticipants.create(config.getMode(), config.policies(),
            number, scenario.bots(), scenario.game()));
        return run;
    }

    /**
     * Один атомарный шаг: блокировка игры → начальное открытие или выбор новых
     * планов → проверка/журналирование → торговля/платежи → сохранение дня.
     * false означает, что рабочему потоку больше нечего выполнять (пауза, финиш).
     * При исключении транзакция дня откатывается, а Manager решает о повторе.
     */
    public boolean advance(Long runId) {
        return Boolean.TRUE.equals(transactions.execute(tx -> {
            var run = runs.findById(runId).orElseThrow();
            if (!"RUNNING".equals(run.getStatus())) return false;
            var group = series.findById(run.getSeries().getId()).orElseThrow();
            if (!"RUNNING".equals(group.getStatus())) {
                stopOrQueue(run, group);
                return false;
            }
            Game game = games.lockById(run.getGame().getId()).orElseThrow();
            if (game.getStatus() == GameStatus.CREATED) startGame(run, group, game);
            if (game.getCurrentDay() >= run.getHorizon()) {
                finishGame(run, group, game);
                return false;
            }
            decideAndApplyProduction(run, group, game);
            int before = game.getCurrentDay();
            // Обычный торговый сервис проводит покупателя, кредиты, налоги и продажи.
            // Решения, платёжные записи и статистика дня коммитятся вместе.
            gameSimulation.tradeDay(game);
            if (game.getCurrentDay() != before + 1)
                throw new IllegalStateException("Торговый день не продвинулся");
            run.setCompletedDays(game.getCurrentDay());
            return true;
        }));
    }

    private void stopOrQueue(ExperimentRun run, ExperimentSeries group) {
        if (!"STOPPED".equals(group.getStatus())) {
            run.setStatus("QUEUED");
            return;
        }
        run.setStatus("CANCELLED");
        invalidatePair(run);
        if (run.getGame().getStatus() != GameStatus.FINISHED) gameSimulation.finish(run.getGame());
    }

    private record ProposedDecision(Manufacturer player, StrategySnapshot view,
                                    BotPolicies.Decision decision, int index) {}

    private ProposedDecision propose(ExperimentRun run, ExperimentSeries group,
                                     Manufacturer player, StrategySnapshot view, int index) {
        return new ProposedDecision(player, view, botDecisions.decide(run.getSeed(), group.getPolicyVersion(),
            group.getModelVersion(), player, view, index), index);
    }

    private void startGame(ExperimentRun run, ExperimentSeries group, Game game) {
        var players = ordered(game.getId());
        var views = gameDayReadModel.snapshotsBeforeDecisions(game.getId());
        var proposed = new ArrayList<ProposedDecision>();
        // Решения всех ботов вычисляются до применения первого производства.
        for (int i = 0; i < players.size(); i++) {
            var player = players.get(i);
            var view = views.get(player.getAccount().getId());
            proposed.add(propose(run, group, player, view, i));
        }
        trainingCapture.withPreDecisionSnapshots(run.getId(), views, () -> {
            for (var choice : proposed) {
                var decisionLog = journal.logDecision(run, choice.player(), choice.view(), choice.decision(),
                    "ACCEPTED", group.getModelVersion());
                var initial = new InitialProductionParameters();
                copy(choice.decision().action(), initial);
                initial.setProductionCapacityPerDay(10 + choice.index() % 5);
                var cycle = manufacturerService.defineInitialProductionParameters(
                    initial, choice.player().getAccount().getId(), game);
                journal.acceptCycle(decisionLog, cycle);
                journal.recordDecision(run, choice.decision());
            }
        });
        game.setStatus(GameStatus.STARTED);
    }

    private void finishGame(ExperimentRun run, ExperimentSeries group, Game game) {
        gameSimulation.finish(game);
        run.setCompletedDays(game.getCurrentDay());
        var players = ordered(game.getId());
        var counts = players.stream().mapToLong(player -> productions.countByManufacturerId(player.getId())).toArray();
        run.setCycles((int) java.util.Arrays.stream(counts).sum());
        run.setBestResult(results.findByGameId(game.getId()).stream().map(GameResult::getResult)
            .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO));
        // COMPLETED требует двух производств у каждого и применения модели в модельной паре.
        boolean enough = java.util.Arrays.stream(counts).allMatch(count -> count >= 2);
        boolean valid = enough && (!"MODEL".equals(players.get(0).getSelectedStrategy()) || run.getModelDecisions() > 0);
        completePair(run, group, valid);
    }

    private void decideAndApplyProduction(ExperimentRun run, ExperimentSeries group, Game game) {
        var players = ordered(game.getId());
        var views = gameDayReadModel.snapshotsBeforeDecisions(game.getId());
        var latest = productions.findByManufacturer_Game_IdOrderByStartDateAscIdAsc(game.getId()).stream()
            .collect(java.util.stream.Collectors.toMap(p -> p.getManufacturer().getId(), p -> p, (first, second) -> second));
        var proposed = new ArrayList<ProposedDecision>();
        // Сначала собираем все снимки и решения; до этого момента никто не делает ход.
        for (int i = 0; i < players.size(); i++) {
            var player = players.get(i);
            var last = latest.get(player.getId());
            if (last == null) continue;
            if (game.getCurrentDay() < last.getStartDate() + last.getTimeToMarket() + 1 ||
                game.getCurrentDay() >= run.getHorizon() - 5) continue;
            var view = views.get(player.getAccount().getId());
            if ("bots-v6".equals(group.getPolicyVersion()) && journal.unchangedAfterSkip(run, player, view)) continue;
            proposed.add(propose(run, group, player, view, i));
        }
        Map<Long, StrategySnapshot> candidateViews = proposed.stream().collect(java.util.stream.Collectors.toMap(
            choice -> choice.player().getAccount().getId(), ProposedDecision::view));
        trainingCapture.withPreDecisionSnapshots(run.getId(), candidateViews, () -> {
            for (var choice : proposed) applyProduction(run, group, game, choice);
        });
    }

    private void applyProduction(ExperimentRun run, ExperimentSeries group, Game game, ProposedDecision choice) {
        var player = choice.player();
        var plan = choice.decision().action();
        // Старые версии политик сохраняют историческое поведение без проверки склада.
        if (group.getPolicyVersion() != null && player.getCurrentProductCount() > plan.getProductCount()) {
            journal.logDecision(run, player, choice.view(), choice.decision(), "STOCK_SKIP", group.getModelVersion());
            run.setStockSkips(run.getStockSkips() + 1);
            return;
        }
        BigDecimal cost = game.getBaseCostPrice().multiply(plan.getQualityIndex())
            .multiply(BigDecimal.valueOf(plan.getProductCount()))
            .add(game.getBaseAdvertisementPrice().multiply(BigDecimal.valueOf(
                plan.getAdvertisingIntensityIndex() * plan.getAdvertisingDays())));
        BigDecimal shortfall = cost.subtract(player.getBalance()).max(BigDecimal.ZERO);
        if (creditGuard.reject(group.getPolicyVersion(), player, game, plan, choice.view(), cost, shortfall)) {
            journal.logDecision(run, player, choice.view(), choice.decision(), "CREDIT_SKIP", group.getModelVersion());
            run.setCreditSkips(run.getCreditSkips() + 1);
            return;
        }
        var next = new NewProductionParameters();
        copy(plan, next);
        next.setBusinessCreditAmount(shortfall);
        var decisionLog = journal.logDecision(run, player, choice.view(), choice.decision(),
            "ACCEPTED", group.getModelVersion());
        var cycle = manufacturerService.defineNewProductionParameters(next, player.getAccount().getId(), game);
        journal.acceptCycle(decisionLog, cycle);
        journal.recordDecision(run, choice.decision());
    }

    private List<Manufacturer> ordered(Long gameId) {
        return manufacturers.findByGame_Id(gameId).stream().sorted(Comparator.comparing(Manufacturer::getId)).toList();
    }

    private static void copy(CommonProductionParameters source, CommonProductionParameters target) {
        target.setProductCount(source.getProductCount()); target.setPrice(source.getPrice());
        target.setQualityIndex(source.getQualityIndex()); target.setAssortment(source.getAssortment());
        target.setAdvertisingIntensityIndex(source.getAdvertisingIntensityIndex());
        target.setAdvertisingDays(source.getAdvertisingDays());
    }

    /**
     * INCOMPLETE — критерий пригодности, не ошибка исполнения. Для EVALUATE обе
     * половины становятся COMPLETED только после удачного завершения партнёра;
     * если одна непригодна, в статистику эффекта не должна попасть вся пара.
     */
    private void completePair(ExperimentRun run, ExperimentSeries group, boolean valid) {
        if (!"EVALUATE".equals(group.getMode())) {
            run.setStatus(valid ? "COMPLETED" : "INCOMPLETE");
            return;
        }
        var pair = pairs.lockById(run.getPair().getId()).orElseThrow();
        pair.finish(run, runs.findByPairId(pair.getId()), valid);
    }

    private void invalidatePair(ExperimentRun run) {
        if (run.getPair() == null) return;
        var pair = pairs.lockById(run.getPair().getId()).orElseThrow();
        pair.invalidate(runs.findByPairId(pair.getId()));
    }
}
