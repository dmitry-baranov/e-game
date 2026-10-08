package ru.itis.diploma.experiment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.itis.diploma.dto.*;
import ru.itis.diploma.model.*;
import ru.itis.diploma.model.enums.GameStatus;
import ru.itis.diploma.repository.*;
import ru.itis.diploma.service.*;
import ru.itis.diploma.service.impl.GameServiceImpl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service @RequiredArgsConstructor
public class ExperimentEngine {
    private final TransactionTemplate transactions;
    private final ExperimentRunRepository runs;
    private final ExperimentSeriesRepository series;
    private final ExperimentScenarios scenarios;
    private final AccountRepository accounts;
    private final ManufacturerRepository manufacturers;
    private final GameRepository games;
    private final GameResultRepository results;
    private final GameServiceImpl gameService;
    private final ManufacturerService manufacturerService;
    private final StrategySnapshotService snapshots;
    private final StrategyTrainingCapture trainingCapture;
    private final BotPolicies policies;
    private final TradingSessionService trading;
    private final ProductionParametersRepository productions;

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
            var queued = runs.findBySeriesIdOrderByNumberDesc(seriesId).stream()
                .filter(r -> "QUEUED".equals(r.getStatus())).findFirst();
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
            List<Long> ids = new ArrayList<>();
            for (int i = 0; i < scenario.bots(); i++) {
                var account = Account.builder().email("bot-" + seriesId + "-" + number + "-" + i + "@experiment.invalid")
                    .fullName("Бот " + (i + 1)).password("!disabled!").role(Account.Role.USER).build();
                ids.add(accounts.save(account).getId());
            }
            scenario.game().setAccountIds(ids);
            // No ordinary UI game limit applies to this isolated deployment.
            var game = gameService.createGameForExperiment(scenario.game());
            run.setGame(game);
            var players = manufacturers.findByGame_Id(game.getId());
            for (int i = 0; i < players.size(); i++) {
                var player = players.get(i);
                player.setSelectedStrategy("EVALUATE".equals(config.getMode()) && i == 0 ?
                    (number % 2 == 1 ? "EVAL_RULES" : "MODEL") : config.policies()[i % config.policies().length]);
                player.setAllowStrategyTraining("COLLECT".equals(config.getMode()));
            }
            return run.getId();
        });
    }

    public boolean advance(Long runId) {
        return Boolean.TRUE.equals(transactions.execute(tx -> {
            var run = runs.findById(runId).orElseThrow();
            if (!"RUNNING".equals(run.getStatus())) return false;
            var group = series.findById(run.getSeries().getId()).orElseThrow();
            if (!"RUNNING".equals(group.getStatus())) {
                if ("STOPPED".equals(group.getStatus())) {
                    run.setStatus("CANCELLED");
                    if (run.getGame().getStatus() != GameStatus.FINISHED) gameService.finishGame(run.getGame().getId());
                } else run.setStatus("QUEUED");
                return false;
            }
            Game game = games.lockById(run.getGame().getId()).orElseThrow();
            if (game.getStatus() == GameStatus.CREATED) {
                var players = ordered(game.getId());
                var proposed = new ArrayList<BotPolicies.Decision>();
                Map<Long, StrategySnapshot> views = new HashMap<>();
                for (int i = 0; i < players.size(); i++) {
                    Long accountId = players.get(i).getAccount().getId();
                    var view = snapshots.snapshot(game.getId(), accountId);
                    views.put(accountId, view);
                    proposed.add(policies.decide(view, players.get(i).getSelectedStrategy(), policySeed(run, group, i), i,
                        group.getModelVersion()));
                }
                trainingCapture.withPreDecisionSnapshots(views, () -> {
                    for (int i = 0; i < players.size(); i++) {
                        var initial = new InitialProductionParameters();
                        copy(proposed.get(i).action(), initial);
                        initial.setProductionCapacityPerDay(10 + i % 5);
                        manufacturerService.defineInitialProductionParameters(initial, players.get(i).getAccount().getId(), game);
                        recordDecision(run, proposed.get(i));
                    }
                });
                game.setStatus(GameStatus.STARTED);
            }
            if (game.getCurrentDay() >= run.getHorizon()) {
                gameService.finishGame(game.getId());
                run.setCompletedDays(game.getCurrentDay());
                var counts = ordered(game.getId()).stream().mapToLong(m -> productions.countByManufacturerId(m.getId())).toArray();
                run.setCycles((int) java.util.Arrays.stream(counts).sum());
                run.setBestResult(results.findByGameId(game.getId()).stream().map(GameResult::getResult)
                    .max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO));
                boolean enough = java.util.Arrays.stream(counts).allMatch(n -> n >= 2);
                boolean valid = enough && (!"MODEL".equals(ordered(game.getId()).get(0).getSelectedStrategy()) ||
                    run.getModelDecisions() > 0);
                completePair(run, series.lockById(group.getId()).orElseThrow(), valid);
                return false;
            }
            // Capture all views before applying any decisions; every game day is one DB transaction.
            var players = ordered(game.getId());
            var pending = new ArrayList<Integer>();
            var proposed = new ArrayList<BotPolicies.Decision>();
            Map<Long, StrategySnapshot> views = new HashMap<>();
            for (int i = 0; i < players.size(); i++) {
                var player = players.get(i);
                var last = manufacturerService.getLastProductionParameters(player.getId()).orElseThrow();
                if (game.getCurrentDay() >= last.getStartDate() + last.getTimeToMarket() + 1 &&
                    game.getCurrentDay() < run.getHorizon() - 5) {
                    pending.add(i);
                    var view = snapshots.snapshot(game.getId(), player.getAccount().getId());
                    views.put(player.getAccount().getId(), view);
                    proposed.add(policies.decide(view, player.getSelectedStrategy(), policySeed(run, group, i), i,
                        group.getModelVersion()));
                }
            }
            trainingCapture.withPreDecisionSnapshots(views, () -> {
                for (int j = 0; j < pending.size(); j++) {
                    int i = pending.get(j);
                    var p = proposed.get(j).action();
                    if (group.getPolicyVersion() != null && players.get(i).getCurrentProductCount() > p.getProductCount()) {
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
                    if (shortfall.compareTo(game.getBaseCostPrice().multiply(BigDecimal.valueOf(80))) > 0) {
                        run.setCreditSkips(run.getCreditSkips() + 1);
                        continue;
                    }
                    next.setBusinessCreditAmount(shortfall);
                    manufacturerService.defineNewProductionParameters(next, players.get(i).getAccount().getId(), game);
                    recordDecision(run, proposed.get(j));
                }
            });
            int before = game.getCurrentDay();
            trading.doDaysActivities(game);
            if (game.getCurrentDay() != before + 1)
                throw new IllegalStateException("Торговый день не продвинулся");
            run.setCompletedDays(game.getCurrentDay());
            return true;
        }));
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

    private void recordDecision(ExperimentRun run, BotPolicies.Decision decision) {
        if (decision.usedModel()) run.setModelDecisions(run.getModelDecisions() + 1);
        if (decision.fallback()) run.setModelFallbacks(run.getModelFallbacks() + 1);
    }

    /** Distinct deterministic RNG for bots: do not hand them the world's scenario seed. */
    private long policySeed(ExperimentRun run, ExperimentSeries group, int index) {
        if (group.getPolicyVersion() == null || !group.getPolicyVersion().equals("bots-v5"))
            return run.getSeed(); // Preserve previously started simulations.
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update("bot-policy-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var bytes = java.nio.ByteBuffer.allocate(16).putLong(run.getSeed()).putLong(index).array();
            return java.nio.ByteBuffer.wrap(digest.digest(bytes)).getLong();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }

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
