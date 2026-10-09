package ru.itis.diploma.experiment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final BotDecisionLogRepository decisionLogs;
    private final JdbcTemplate jdbc;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

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
                int rotation = "EVALUATE".equals(config.getMode()) ? (number - 1) / 2 : number - 1;
                player.setSelectedStrategy("EVALUATE".equals(config.getMode()) && i == 0 ?
                    (number % 2 == 1 ? "EVAL_RULES" : "MODEL") :
                    config.policies()[(rotation + i - ("EVALUATE".equals(config.getMode()) ? 1 : 0)
                        + config.policies().length) % config.policies().length]);
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
                var competitors = snapshots.visibleCompetitors(players);
                var proposed = new ArrayList<BotPolicies.Decision>();
                Map<Long, StrategySnapshot> views = new HashMap<>();
                for (int i = 0; i < players.size(); i++) {
                    Long accountId = players.get(i).getAccount().getId();
                    var view = snapshots.snapshot(game.getId(), accountId, competitors);
                    views.put(accountId, view);
                    proposed.add(decide(run, group, players.get(i), view, i));
                }
                trainingCapture.withPreDecisionSnapshots(views, () -> {
                    for (int i = 0; i < players.size(); i++) {
                        logDecision(run, players.get(i), views.get(players.get(i).getAccount().getId()),
                            proposed.get(i), "ACCEPTED", group.getModelVersion());
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
            Map<Long, StrategySnapshot.Competitor> competitors = null;
            for (int i = 0; i < players.size(); i++) {
                var player = players.get(i);
                var last = manufacturerService.getLastProductionParameters(player.getId()).orElseThrow();
                if (game.getCurrentDay() >= last.getStartDate() + last.getTimeToMarket() + 1 &&
                    game.getCurrentDay() < run.getHorizon() - 5) {
                    if (competitors == null) competitors = snapshots.visibleCompetitors(players);
                    var view = snapshots.snapshot(game.getId(), player.getAccount().getId(), competitors);
                    if ("bots-v6".equals(group.getPolicyVersion()) && unchangedAfterSkip(run, player, view)) continue;
                    pending.add(i);
                    views.put(player.getAccount().getId(), view);
                    proposed.add(decide(run, group, player, view, i));
                }
            }
            trainingCapture.withPreDecisionSnapshots(views, () -> {
                for (int j = 0; j < pending.size(); j++) {
                    int i = pending.get(j);
                    var p = proposed.get(j).action();
                    if (group.getPolicyVersion() != null && players.get(i).getCurrentProductCount() > p.getProductCount()) {
                        logDecision(run, players.get(i), views.get(players.get(i).getAccount().getId()),
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
                        logDecision(run, players.get(i), views.get(players.get(i).getAccount().getId()),
                            proposed.get(j), "CREDIT_SKIP", group.getModelVersion());
                        run.setCreditSkips(run.getCreditSkips() + 1);
                        continue;
                    }
                    if (shortfall.compareTo(game.getBaseCostPrice().multiply(BigDecimal.valueOf(80))) > 0) {
                        logDecision(run, players.get(i), views.get(players.get(i).getAccount().getId()),
                            proposed.get(j), "CREDIT_SKIP", group.getModelVersion());
                        run.setCreditSkips(run.getCreditSkips() + 1);
                        continue;
                    }
                    next.setBusinessCreditAmount(shortfall);
                    logDecision(run, players.get(i), views.get(players.get(i).getAccount().getId()),
                        proposed.get(j), "ACCEPTED", group.getModelVersion());
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

    private BigDecimal viewPaymentReserve(StrategySnapshot view, int untilDay) {
        return view.payments().stream().filter(p -> p.day() > view.day() && p.day() <= untilDay)
            .map(StrategySnapshot.Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean unchangedAfterSkip(ExperimentRun run, Manufacturer player, StrategySnapshot view) {
        var previous = decisionLogs.findFirstByRunIdAndManufacturerIdOrderByDecisionDayDescIdDesc(
            run.getId(), player.getId()).orElse(null);
        if (previous == null || !java.util.Set.of("STOCK_SKIP", "CREDIT_SKIP").contains(previous.getOutcome())) return false;
        if (view.day() < previous.getDecisionDay() + 3) return true;
        try {
            var before = mapper.readTree(previous.getSnapshotJson());
            if ("STOCK_SKIP".equals(previous.getOutcome()))
                return view.stock() >= before.path("stock").asInt();
            return view.balance().compareTo(new BigDecimal(before.path("balance").asText())) <= 0;
        } catch (com.fasterxml.jackson.core.JsonProcessingException | NumberFormatException e) {
            return false;
        }
    }

    private BotPolicies.Decision decide(ExperimentRun run, ExperimentSeries group, Manufacturer player,
                                         StrategySnapshot view, int index) {
        long seed = policySeed(run, group, index);
        if (!"bots-v6".equals(group.getPolicyVersion()))
            return policies.decide(view, player.getSelectedStrategy(), seed, index, group.getModelVersion());
        return policies.decide(view, player.getSelectedStrategy(), seed, index, group.getModelVersion(),
            observedCycles(player.getId(), view.day()));
    }

    private List<ObservedCycle> observedCycles(Long manufacturerId, int day) {
        return jdbc.query("""
            SELECT p.start_date, p.time_to_market, p.product_count, p.price, p.quality_index, p.assortment,
                   coalesce((SELECT a.intensity_index FROM advertisement a WHERE a.manufacturer_id=p.manufacturer_id
                       AND a.start_date=p.start_date+1 ORDER BY a.id DESC LIMIT 1),0) AS ads,
                   coalesce((SELECT a.end_date-a.start_date+1 FROM advertisement a WHERE a.manufacturer_id=p.manufacturer_id
                       AND a.start_date=p.start_date+1 ORDER BY a.id DESC LIMIT 1),0) AS ad_days,
                   coalesce(sum(s.products_sold),0) AS sold,
                   coalesce(sum(s.products_sold * p.price),0) AS revenue
            FROM production_parameters p LEFT JOIN statistics_info s ON s.manufacturer_id=p.manufacturer_id
                 AND s.trade_date>p.start_date AND s.trade_date<=p.start_date+p.time_to_market
            WHERE p.manufacturer_id=? AND p.start_date+p.time_to_market<=?
            GROUP BY p.id HAVING count(DISTINCT s.trade_date)=p.time_to_market
            ORDER BY p.start_date
            """, (rs, row) -> new ObservedCycle(rs.getInt("start_date"), rs.getInt("time_to_market"),
                rs.getInt("product_count"), rs.getBigDecimal("price"), rs.getBigDecimal("quality_index"),
                rs.getInt("assortment"), rs.getInt("ads"), rs.getInt("ad_days"), rs.getInt("sold"),
                rs.getBigDecimal("revenue")), manufacturerId, day);
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

    private void logDecision(ExperimentRun run, Manufacturer player, StrategySnapshot snapshot,
                             BotPolicies.Decision decision, String outcome, String modelVersion) {
        try {
            var log = new BotDecisionLog();
            log.setRun(run);
            log.setManufacturer(player);
            log.setDecisionDay(snapshot.day());
            log.setPolicy(player.getSelectedStrategy());
            log.setModelVersion(modelVersion);
            log.setOutcome(outcome);
            log.setUsedModel(decision.usedModel());
            log.setFallback(decision.fallback());
            log.setFallbackReason(decision.fallbackReason());
            log.setRecommendationSource(decision.usedModel() ? "MODEL" :
                decision.fallback() ? "RULES_FALLBACK" : player.getSelectedStrategy());
            log.setSnapshotJson(mapper.writeValueAsString(snapshot));
            log.setProposedActionJson(mapper.writeValueAsString(decision.action()));
            log.setCandidatesJson(mapper.writeValueAsString(decision.candidates()));
            decisionLogs.save(log);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сохранить решение бота", e);
        }
    }

    /** Distinct deterministic RNG for bots: do not hand them the world's scenario seed. */
    private long policySeed(ExperimentRun run, ExperimentSeries group, int index) {
        if (group.getPolicyVersion() == null ||
            (!group.getPolicyVersion().equals("bots-v5") && !group.getPolicyVersion().equals("bots-v6")))
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
