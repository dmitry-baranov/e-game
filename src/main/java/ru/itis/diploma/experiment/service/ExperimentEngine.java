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
            if ("EVALUATE".equals(group.getMode())) {
                for (var waiting : runs.findBySeriesIdOrderByNumberDesc(seriesId)) {
                    if (!"WAITING_PAIR".equals(waiting.getStatus())) continue;
                    int other = waiting.getNumber() % 2 == 0 ? waiting.getNumber() - 1 : waiting.getNumber() + 1;
                    runs.findBySeriesIdAndNumber(seriesId, other).ifPresent(partner -> {
                        if ("WAITING_PAIR".equals(partner.getStatus())) {
                            waiting.setStatus("COMPLETED"); partner.setStatus("COMPLETED");
                        } else if ("INCOMPLETE".equals(partner.getStatus()) ||
                            "ERROR".equals(partner.getStatus()) || "CANCELLED".equals(partner.getStatus()))
                            waiting.setStatus("INCOMPLETE");
                    });
                }
                runs.flush();
            }
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
            int number = group.getNextNumber();
            group.setNextNumber(number + 1);
            var scenario = scenarios.generate(config, number, group.getGeneratorVersion());
            var run = new ExperimentRun();
            run.setSeries(group); run.setNumber(number); run.setSeed(scenario.seed());
            run.setMarket(scenario.market()); run.setBots(scenario.bots()); run.setHorizon(scenario.horizon());
            runs.save(run);
            run.setGame(botParticipants.create(group.getId(), config.getMode(), config.policies(),
                number, scenario.bots(), scenario.game()));
            return run.getId();
        });
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
                if ("STOPPED".equals(group.getStatus())) {
                    run.setStatus("CANCELLED");
                    if (run.getGame().getStatus() != GameStatus.FINISHED) gameSimulation.finish(run.getGame());
                } else run.setStatus("QUEUED");
                return false;
            }
            Game game = games.lockById(run.getGame().getId()).orElseThrow();
            if (game.getStatus() == GameStatus.CREATED) {
                // На старте каждый бот получает снимок ещё не открытой игры;
                // все предложения вычисляются до сохранения первого производства.
                var players = ordered(game.getId());
                var competitors = snapshots.visibleCompetitors(players);
                var proposed = new ArrayList<BotPolicies.Decision>();
                Map<Long, StrategySnapshot> views = new HashMap<>();
                for (int i = 0; i < players.size(); i++) {
                    Long accountId = players.get(i).getAccount().getId();
                    var view = snapshots.snapshot(game.getId(), accountId, competitors);
                    views.put(accountId, view);
                    proposed.add(botDecisions.decide(run.getSeed(), group.getPolicyVersion(),
                        group.getModelVersion(), players.get(i), view, i));
                }
                trainingCapture.withPreDecisionSnapshots(views, () -> {
                    for (int i = 0; i < players.size(); i++) {
                        journal.logDecision(run, players.get(i), views.get(players.get(i).getAccount().getId()),
                            proposed.get(i), "ACCEPTED", group.getModelVersion());
                        var initial = new InitialProductionParameters();
                        copy(proposed.get(i).action(), initial);
                        initial.setProductionCapacityPerDay(10 + i % 5);
                        manufacturerService.defineInitialProductionParameters(initial, players.get(i).getAccount().getId(), game);
                        journal.recordDecision(run, proposed.get(i));
                    }
                });
                game.setStatus(GameStatus.STARTED);
            }
            if (game.getCurrentDay() >= run.getHorizon()) {
                gameSimulation.finish(game);
                run.setCompletedDays(game.getCurrentDay());
                var counts = ordered(game.getId()).stream().mapToLong(m -> productions.countByManufacturerId(m.getId())).toArray();
                run.setCycles((int) java.util.Arrays.stream(counts).sum());
                run.setBestResult(results.findByGameId(game.getId()).stream().map(GameResult::getResult)
                    .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO));
                // COMPLETED требует хотя бы две записи производства у КАЖДОГО бота.
                // Полнота дней учебной метки — отдельная проверка при построении отчёта.
                boolean enough = java.util.Arrays.stream(counts).allMatch(n -> n >= 2);
                boolean valid = enough && (!"MODEL".equals(ordered(game.getId()).get(0).getSelectedStrategy()) ||
                    run.getModelDecisions() > 0);
                completePair(run, series.lockById(group.getId()).orElseThrow(), valid);
                return false;
            }
            // Сначала собираем разрешённые снимки ВСЕХ готовых к решению ботов;
            // сервер ещё не применил ни один новый план этого дня.
            var players = ordered(game.getId());
            var pending = new ArrayList<Integer>();
            var proposed = new ArrayList<BotPolicies.Decision>();
            Map<Long, StrategySnapshot> views = new HashMap<>();
            Map<Long, StrategySnapshot.Competitor> competitors = null;
            for (int i = 0; i < players.size(); i++) {
                var player = players.get(i);
                var last = manufacturerService.getLastProductionParameters(player.getId()).orElseThrow();
                if (game.getCurrentDay() >= last.getStartDate() + last.getTimeToMarket() + 1 &&
                    game.getCurrentDay() < run.getHorizon() - 5) {
                    if (competitors == null) competitors = snapshots.visibleCompetitors(players);
                    var view = snapshots.snapshot(game.getId(), player.getAccount().getId(), competitors);
                    if ("bots-v6".equals(group.getPolicyVersion()) && journal.unchangedAfterSkip(run, player, view)) continue;
                    pending.add(i);
                    views.put(player.getAccount().getId(), view);
                    proposed.add(botDecisions.decide(run.getSeed(), group.getPolicyVersion(),
                        group.getModelVersion(), player, view, i));
                }
            }
            trainingCapture.withPreDecisionSnapshots(views, () -> {
                for (int j = 0; j < pending.size(); j++) {
                    int i = pending.get(j);
                    var p = proposed.get(j).action();
                    // STOCK_SKIP/CREDIT_SKIP фиксируют предложенный, но НЕ выполненный план.
                    // Для старых policyVersion сохраняется историческая проверка склада.
                    if (group.getPolicyVersion() != null && players.get(i).getCurrentProductCount() > p.getProductCount()) {
                        journal.logDecision(run, players.get(i), views.get(players.get(i).getAccount().getId()),
                            proposed.get(j), "STOCK_SKIP", group.getModelVersion());
                        run.setStockSkips(run.getStockSkips() + 1);
                        continue;
                    }
                    var next = new NewProductionParameters();
                    copy(p, next);
                    BigDecimal cost = game.getBaseCostPrice().multiply(p.getQualityIndex())
                        .multiply(BigDecimal.valueOf(p.getProductCount()))
                        .add(game.getBaseAdvertisementPrice().multiply(BigDecimal.valueOf(p.getAdvertisingIntensityIndex() * p.getAdvertisingDays())));
                    var balance = players.get(i).getBalance();
                    // Conservative cap on borrowing; unaffordable cycles are explicitly skipped.
                    BigDecimal shortfall = cost.subtract(balance).max(BigDecimal.ZERO);
                    String policy = players.get(i).getSelectedStrategy();
                    if ("bots-v6".equals(group.getPolicyVersion()) &&
                        (("DEBT_AVOID".equals(policy) && shortfall.signum() > 0) ||
                         ("CASH_RESERVE".equals(policy) &&
                             balance.subtract(cost).compareTo(balance.max(BigDecimal.ZERO).multiply(new BigDecimal("0.25"))) < 0) ||
                         ("PAYMENT_AWARE".equals(policy) &&
                             balance.subtract(cost).compareTo(viewPaymentReserve(views.get(players.get(i).getAccount().getId()),
                                 game.getCurrentDay() + (p.getProductCount() + players.get(i).getProductionCapacityPerDay() - 1)
                                     / players.get(i).getProductionCapacityPerDay())) < 0) ||
                         ("LIMITED_CREDIT_GROWTH".equals(policy) &&
                             shortfall.compareTo(game.getBaseCostPrice().multiply(BigDecimal.valueOf(20))) > 0))) {
                        journal.logDecision(run, players.get(i), views.get(players.get(i).getAccount().getId()),
                            proposed.get(j), "CREDIT_SKIP", group.getModelVersion());
                        run.setCreditSkips(run.getCreditSkips() + 1);
                        continue;
                    }
                    if (shortfall.compareTo(game.getBaseCostPrice().multiply(BigDecimal.valueOf(80))) > 0) {
                        journal.logDecision(run, players.get(i), views.get(players.get(i).getAccount().getId()),
                            proposed.get(j), "CREDIT_SKIP", group.getModelVersion());
                        run.setCreditSkips(run.getCreditSkips() + 1);
                        continue;
                    }
                    next.setBusinessCreditAmount(shortfall);
                    journal.logDecision(run, players.get(i), views.get(players.get(i).getAccount().getId()),
                        proposed.get(j), "ACCEPTED", group.getModelVersion());
                    manufacturerService.defineNewProductionParameters(next, players.get(i).getAccount().getId(), game);
                    journal.recordDecision(run, proposed.get(j));
                }
            });
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
