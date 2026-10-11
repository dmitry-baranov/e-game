package ru.itis.diploma.bot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.itis.diploma.dto.StrategySnapshot;
import ru.itis.diploma.experiment.BotDecisionLog;
import ru.itis.diploma.experiment.BotDecisionLogRepository;
import ru.itis.diploma.experiment.ExperimentRun;
import ru.itis.diploma.experiment.ExperimentSeries;
import ru.itis.diploma.model.Manufacturer;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;

/** A bot sees its pre-decision snapshot, not the hidden buyer settings or other bots' actions. */
@Service
@RequiredArgsConstructor
public class BotDecisions {
    private final BotPolicies policies;
    private final BotDecisionLogRepository decisionLogs;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public BotPolicies.Decision decide(ExperimentRun run, ExperimentSeries group, Manufacturer player,
                                       StrategySnapshot view, int index) {
        long seed = policySeed(run, group, index);
        if (!"bots-v6".equals(group.getPolicyVersion()))
            return policies.decide(view, player.getSelectedStrategy(), seed, index, group.getModelVersion());
        return policies.decide(view, player.getSelectedStrategy(), seed, index, group.getModelVersion(),
            observedCycles(player.getId(), view.day()));
    }

    /** Avoid retrying a rejected plan until the bot's observable position has changed. */
    public boolean unchangedAfterSkip(ExperimentRun run, Manufacturer player, StrategySnapshot view) {
        var previous = decisionLogs.findFirstByRunIdAndManufacturerIdOrderByDecisionDayDescIdDesc(
            run.getId(), player.getId()).orElse(null);
        if (previous == null || !Set.of("STOCK_SKIP", "CREDIT_SKIP").contains(previous.getOutcome())) return false;
        if (view.day() < previous.getDecisionDay() + 3) return true;
        try {
            var before = mapper.readTree(previous.getSnapshotJson());
            if ("STOCK_SKIP".equals(previous.getOutcome()))
                return view.stock() >= before.path("stock").asInt();
            return view.balance().compareTo(new BigDecimal(before.path("balance").asText())) <= 0;
        } catch (JsonProcessingException | NumberFormatException e) {
            return false;
        }
    }

    /** Completed cycles only; observations are restricted to this manufacturer's past. */
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

    /** Separate deterministic bot RNG; never reveal the world generator's seed to a bot. */
    private long policySeed(ExperimentRun run, ExperimentSeries group, int index) {
        if (group.getPolicyVersion() == null ||
            (!group.getPolicyVersion().equals("bots-v5") && !group.getPolicyVersion().equals("bots-v6")))
            return run.getSeed(); // Preserve games started with earlier policy versions.
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update("bot-policy-v1".getBytes(StandardCharsets.UTF_8));
            var bytes = ByteBuffer.allocate(16).putLong(run.getSeed()).putLong(index).array();
            return ByteBuffer.wrap(digest.digest(bytes)).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }

    public void recordDecision(ExperimentRun run, BotPolicies.Decision decision) {
        if (decision.usedModel()) run.setModelDecisions(run.getModelDecisions() + 1);
        if (decision.fallback()) run.setModelFallbacks(run.getModelFallbacks() + 1);
    }

    /** Saves accepted and rejected actions with the view available before this day's decisions. */
    public void logDecision(ExperimentRun run, Manufacturer player, StrategySnapshot snapshot,
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
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сохранить решение бота", e);
        }
    }
}
