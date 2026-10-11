package ru.itis.diploma.experiment.service;

import ru.itis.diploma.experiment.config.ExperimentConfig;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.itis.diploma.game.dto.*;
import ru.itis.diploma.experiment.bot.BotDecisions;
import ru.itis.diploma.experiment.bot.BotParticipants;
import ru.itis.diploma.experiment.bot.BotPolicies;
import ru.itis.diploma.experiment.model.ExperimentRun;
import ru.itis.diploma.experiment.model.ExperimentSeries;
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
import java.util.HashMap;
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
    private final ExperimentScenarios scenarios;
    private final BotParticipants botParticipants;
    private final ManufacturerRepository manufacturers;
    private final GameRepository games;
    private final GameResultRepository results;
    private final GameSimulation gameSimulation;
    private final ManufacturerService manufacturerService;
    private final StrategySnapshotService snapshots;
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
            if ("EVALUATE".equals(group.getMode())) reconcileWaitingPairs(seriesId);
            long done = runs.countBySeriesIdAndStatus(seriesId, "COMPLETED");
            long active = runs.countBySeriesIdAndStatus(seriesId, "RUNNING");
            long waiting = runs.countBySeriesIdAndStatus(seriesId, "WAITING_PAIR");
            if (done >= config.getTarget()) {
                if (active == 0) group.setStatus("DONE");
                return null;
            }
            if (active >= config.getParallel()) return null;
            var queued = runs.findFirstBySeriesIdAndStatusOrderByNumberDesc(seriesId, "QUEUED");
            if (queued.isPresent()) {
                queued.get().setStatus("RUNNING");
                return queued.get().getId();
            }
            if (done + active + waiting >= config.getTarget()) return null;
            if (group.getNextNumber() > config.getMaxAttempts()) {
                if (active == 0) group.setStatus("EXHAUSTED");
                return null;
            }
            return createRun(group, config).getId();
        });
    }

    private void reconcileWaitingPairs(Long seriesId) {
        for (var waiting : runs.findBySeriesIdOrderByNumberDesc(seriesId)) {
            if (!"WAITING_PAIR".equals(waiting.getStatus())) continue;
            int partnerNumber = waiting.getNumber() % 2 == 0 ? waiting.getNumber() - 1 : waiting.getNumber() + 1;
            runs.findBySeriesIdAndNumber(seriesId, partnerNumber).ifPresent(partner -> {
                if ("WAITING_PAIR".equals(partner.getStatus())) {
                    waiting.setStatus("COMPLETED");
                    partner.setStatus("COMPLETED");
                } else if ("INCOMPLETE".equals(partner.getStatus()) ||
                    "ERROR".equals(partner.getStatus()) || "CANCELLED".equals(partner.getStatus())) {
                    waiting.setStatus("INCOMPLETE");
                }
            });
        }
        runs.flush();
    }

    private ExperimentRun createRun(ExperimentSeries group, ExperimentConfig config) {
        int number = group.getNextNumber();
        group.setNextNumber(number + 1);
        var scenario = scenarios.generate(config, number, group.getGeneratorVersion());
        var run = new ExperimentRun();
        run.setSeries(group);
        run.setNumber(number);
        run.setSeed(scenario.seed());
        run.setMarket(scenario.market());
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
        var competitors = snapshots.visibleCompetitors(players);
        var proposed = new ArrayList<ProposedDecision>();
        Map<Long, StrategySnapshot> views = new HashMap<>();
        // Решения всех ботов вычисляются до применения первого производства.
        for (int i = 0; i < players.size(); i++) {
            var player = players.get(i);
            var view = snapshots.snapshot(game.getId(), player.getAccount().getId(), competitors);
            views.put(player.getAccount().getId(), view);
            proposed.add(propose(run, group, player, view, i));
        }
        trainingCapture.withPreDecisionSnapshots(views, () -> {
            for (var choice : proposed) {
                journal.logDecision(run, choice.player(), choice.view(), choice.decision(), "ACCEPTED", group.getModelVersion());
                var initial = new InitialProductionParameters();
                copy(choice.decision().action(), initial);
                initial.setProductionCapacityPerDay(10 + choice.index() % 5);
                manufacturerService.defineInitialProductionParameters(initial, choice.player().getAccount().getId(), game);
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
        completePair(run, series.lockById(group.getId()).orElseThrow(), valid);
    }

    private void decideAndApplyProduction(ExperimentRun run, ExperimentSeries group, Game game) {
        var players = ordered(game.getId());
        var proposed = new ArrayList<ProposedDecision>();
        Map<Long, StrategySnapshot> views = new HashMap<>();
        Map<Long, StrategySnapshot.Competitor> competitors = null;
        // Сначала собираем все снимки и решения; до этого момента никто не делает ход.
        for (int i = 0; i < players.size(); i++) {
            var player = players.get(i);
            var last = manufacturerService.getLastProductionParameters(player.getId()).orElseThrow();
            if (game.getCurrentDay() < last.getStartDate() + last.getTimeToMarket() + 1 ||
                game.getCurrentDay() >= run.getHorizon() - 5) continue;
            if (competitors == null) competitors = snapshots.visibleCompetitors(players);
            var view = snapshots.snapshot(game.getId(), player.getAccount().getId(), competitors);
            if ("bots-v6".equals(group.getPolicyVersion()) && journal.unchangedAfterSkip(run, player, view)) continue;
            views.put(player.getAccount().getId(), view);
            proposed.add(propose(run, group, player, view, i));
        }
        trainingCapture.withPreDecisionSnapshots(views, () -> {
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
        if (rejectForCredit(group, game, choice, cost, shortfall)) {
            journal.logDecision(run, player, choice.view(), choice.decision(), "CREDIT_SKIP", group.getModelVersion());
            run.setCreditSkips(run.getCreditSkips() + 1);
            return;
        }
        var next = new NewProductionParameters();
        copy(plan, next);
        next.setBusinessCreditAmount(shortfall);
        journal.logDecision(run, player, choice.view(), choice.decision(), "ACCEPTED", group.getModelVersion());
        manufacturerService.defineNewProductionParameters(next, player.getAccount().getId(), game);
        journal.recordDecision(run, choice.decision());
    }

    private boolean rejectForCredit(ExperimentSeries group, Game game, ProposedDecision choice,
                                    BigDecimal cost, BigDecimal shortfall) {
        var player = choice.player();
        var balance = player.getBalance();
        if (shortfall.compareTo(game.getBaseCostPrice().multiply(BigDecimal.valueOf(80))) > 0) return true;
        if (!"bots-v6".equals(group.getPolicyVersion())) return false;
        if (player.getSelectedStrategy() == null) return false;
        return switch (player.getSelectedStrategy()) {
            case "DEBT_AVOID" -> shortfall.signum() > 0;
            case "CASH_RESERVE" -> balance.subtract(cost)
                .compareTo(balance.max(BigDecimal.ZERO).multiply(new BigDecimal("0.25"))) < 0;
            case "PAYMENT_AWARE" -> {
                int days = (choice.decision().action().getProductCount() + player.getProductionCapacityPerDay() - 1)
                    / player.getProductionCapacityPerDay();
                yield balance.subtract(cost).compareTo(viewPaymentReserve(choice.view(), game.getCurrentDay() + days)) < 0;
            }
            case "LIMITED_CREDIT_GROWTH" -> shortfall.compareTo(game.getBaseCostPrice().multiply(BigDecimal.valueOf(20))) > 0;
            default -> false;
        };
    }

    private List<Manufacturer> ordered(Long gameId) {
        return manufacturers.findByGame_Id(gameId).stream().sorted(Comparator.comparing(Manufacturer::getId)).toList();
    }

    private BigDecimal viewPaymentReserve(StrategySnapshot view, int untilDay) {
        return view.payments().stream().filter(p -> p.day() > view.day() && p.day() <= untilDay)
            .map(StrategySnapshot.Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
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
        int partnerNumber = run.getNumber() % 2 == 0 ? run.getNumber() - 1 : run.getNumber() + 1;
        var partner = runs.findBySeriesIdAndNumber(group.getId(), partnerNumber).orElse(null);
        if (!valid) {
            run.setStatus("INCOMPLETE");
            if (partner != null && "WAITING_PAIR".equals(partner.getStatus())) partner.setStatus("INCOMPLETE");
        } else if (partner != null && "WAITING_PAIR".equals(partner.getStatus())) {
            partner.setStatus("COMPLETED");
            run.setStatus("COMPLETED");
        } else if (partner != null && ("ERROR".equals(partner.getStatus()) || "INCOMPLETE".equals(partner.getStatus()))) {
            run.setStatus("INCOMPLETE");
        } else run.setStatus("WAITING_PAIR");
    }
}
