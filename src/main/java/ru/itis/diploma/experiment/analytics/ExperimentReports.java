package ru.itis.diploma.experiment.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

/**
 * Отчёт серии: охват учебных эпизодов и результаты политик считаются только
 * для COMPLETED; все сформированные EVALUATE-пары перечисляются отдельно.
 * Финансовый результат — game_result после долгов, НЕ прибыль за день.
 * Табличные фильтры страницы игр на этот агрегат не влияют.
 */
@Service @RequiredArgsConstructor
public class ExperimentReports {
    private final JdbcTemplate jdbc;

    /** Построение полного отчёта выполняется по запросу, а не при каждом обновлении статуса. */
    public Map<String, Object> report(long seriesId) {
        var coverage = jdbc.queryForMap("""
            SELECT count(*) AS episodes,
                   count(*) FILTER (WHERE observed=cycle_days AND duplicate_days=0) AS complete_cycles,
                   count(*) FILTER (WHERE observed<cycle_days) AS incomplete_cycles,
                   coalesce(sum(duplicate_days),0) AS duplicate_days,
                   count(*) FILTER (WHERE observed=cycle_days AND duplicate_days=0 AND sold=0) AS zero_sales_cycles
            FROM (SELECT e.id, e.cycle_days, count(DISTINCT s.trade_date) AS observed,
                         count(s.id) - count(DISTINCT s.trade_date) AS duplicate_days,
                         coalesce(sum(s.products_sold),0) AS sold
                  FROM strategy_training_episode e
                  JOIN manufacturer m ON m.id=e.manufacturer_id
                  JOIN experiment_run r ON r.game_id=m.game_id
                  LEFT JOIN statistics_info s ON s.manufacturer_id=m.id
                       AND s.trade_date>e.decision_day AND s.trade_date<=e.decision_day+e.cycle_days
                  WHERE r.series_id=? AND r.status='COMPLETED'
                  GROUP BY e.id) cycles
            """, seriesId);
        var policies = jdbc.queryForList("""
            SELECT m.selected_strategy AS policy, count(*) AS players,
                   round(avg(gr.result),2) AS mean_result_after_debt,
                   percentile_disc(0.25) WITHIN GROUP (ORDER BY gr.result) AS q1_result,
                   percentile_disc(0.5) WITHIN GROUP (ORDER BY gr.result) AS median_result,
                   percentile_disc(0.75) WITHIN GROUP (ORDER BY gr.result) AS q3_result,
                   count(*) FILTER (WHERE gr.result>0) AS positive_results,
                   round(avg(gr.result / nullif(g.current_day,0)),2) AS mean_result_per_day,
                   round(avg(m.current_product_count),2) AS mean_stock
            FROM experiment_run r JOIN game g ON g.id=r.game_id
            JOIN manufacturer m ON m.game_id=g.id
            JOIN game_result gr ON gr.manufacturer_id=m.id
            WHERE r.series_id=? AND r.status='COMPLETED'
            GROUP BY m.selected_strategy ORDER BY m.selected_strategy
            """, seriesId);
        var rows = jdbc.queryForList("""
            SELECT r.id AS run_id, r.number, r.seed, r.market, coalesce(r.model_decisions,0) AS model_decisions,
                   coalesce(r.model_fallbacks,0) AS model_fallbacks,
                   gr.result, g.current_day AS days
            FROM experiment_run r JOIN experiment_series es ON es.id=r.series_id
            JOIN game g ON g.id=r.game_id
            JOIN manufacturer m ON m.game_id=g.id
            JOIN game_result gr ON gr.manufacturer_id=m.id
            WHERE r.series_id=? AND es.mode='EVALUATE' AND r.status='COMPLETED' AND m.id=(
                SELECT min(m2.id) FROM manufacturer m2 WHERE m2.game_id=g.id)
            ORDER BY r.number
            """, seriesId);
        List<Map<String, Object>> pairs = new ArrayList<>();
        Map<Integer, Map<String, Object>> first = new HashMap<>();
        // Только завершённые обе половины с одинаковым seed И рынком пригодны
        // для разницы результатов; значение модели минус правила для первого бота.
        for (var row : rows) {
            int number = ((Number) row.get("number")).intValue();
            if (number % 2 == 1) first.put(number, row);
            else if (first.containsKey(number - 1) &&
                Objects.equals(first.get(number - 1).get("seed"), row.get("seed")) &&
                Objects.equals(first.get(number - 1).get("market"), row.get("market"))) {
                var baseline = first.get(number - 1);
                BigDecimal delta = ((BigDecimal) row.get("result")).subtract((BigDecimal) baseline.get("result"));
                Map<String, Object> pair = new LinkedHashMap<>();
                pair.put("pair", number / 2);
                pair.put("seed", row.get("seed").toString());
                pair.put("market", row.get("market"));
                pair.put("rules", baseline.get("result"));
                pair.put("model", row.get("result"));
                pair.put("modelMinusRules", delta);
                pair.put("days", row.get("days"));
                pair.put("rulesRunId", baseline.get("run_id"));
                pair.put("modelRunId", row.get("run_id"));
                pair.put("modelDecisions", row.get("model_decisions"));
                pair.put("modelFallbacks", row.get("model_fallbacks"));
                pairs.add(pair);
            }
        }
        // Отдельный список ВСЕХ созданных пар показывает также незавершённые
        // и непригодные попытки, не смешивая их со средним эффектом выше.
        var allRuns = jdbc.queryForList("""
            SELECT id, number, seed::text AS seed, market, status, model_decisions, model_fallbacks
            FROM experiment_run WHERE series_id=? AND EXISTS
                (SELECT 1 FROM experiment_series es WHERE es.id=experiment_run.series_id AND es.mode='EVALUATE')
            ORDER BY number
            """, seriesId);
        var fallbackReasons = jdbc.queryForList("""
            SELECT coalesce(d.fallback_reason,'UNKNOWN_OLD_RUN') AS reason, count(*) AS opportunities
            FROM bot_decision_log d JOIN experiment_run r ON r.id=d.run_id
            WHERE r.series_id=? AND d.policy='MODEL' AND d.fallback=true
            GROUP BY d.fallback_reason ORDER BY opportunities DESC
            """, seriesId);
        List<Map<String, Object>> allPairs = new ArrayList<>();
        Map<Integer, Map<String, Object>> odd = new HashMap<>();
        for (var r : allRuns) {
            int number = ((Number) r.get("number")).intValue();
            if (number % 2 == 1) odd.put(number, r);
            else if (odd.containsKey(number - 1)) {
                var baseline = odd.get(number - 1);
                Map<String, Object> pair = new LinkedHashMap<>();
                pair.put("pair", number / 2);
                pair.put("seed", r.get("seed"));
                pair.put("market", r.get("market"));
                pair.put("rulesRunId", baseline.get("id"));
                pair.put("modelRunId", r.get("id"));
                pair.put("rulesStatus", baseline.get("status"));
                pair.put("modelStatus", r.get("status"));
                pair.put("modelDecisions", r.get("model_decisions"));
                pair.put("modelFallbacks", r.get("model_fallbacks"));
                pair.put("paired", Objects.equals(baseline.get("seed"), r.get("seed")) &&
                    Objects.equals(baseline.get("market"), r.get("market")));
                allPairs.add(pair);
            }
        }
        var deltas = pairs.stream().map(p -> (BigDecimal) p.get("modelMinusRules")).sorted().toList();
        List<BigDecimal> bootstrap = new ArrayList<>();
        if (deltas.size() >= 2) {
            var random = new java.util.Random(42);
            for (int sample = 0; sample < 1000; sample++) {
                BigDecimal total = BigDecimal.ZERO;
                for (int i = 0; i < deltas.size(); i++) total = total.add(deltas.get(random.nextInt(deltas.size())));
                bootstrap.add(total.divide(BigDecimal.valueOf(deltas.size()), 2, java.math.RoundingMode.HALF_UP));
            }
            bootstrap.sort(Comparator.naturalOrder());
        }
        return Map.of("coverage", coverage, "policies", policies, "pairs", pairs,
            "fallbackReasons", fallbackReasons,
            "allPairs", allPairs, "medianPairedAdvantage", deltas.isEmpty() ? "нет данных" :
                (deltas.size() % 2 == 1 ? deltas.get(deltas.size() / 2) :
                    deltas.get(deltas.size() / 2 - 1).add(deltas.get(deltas.size() / 2))
                        .divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP)).toString(),
            "modelWinShare", deltas.isEmpty() ? "нет данных" :
                BigDecimal.valueOf(deltas.stream().filter(d -> d.signum() > 0).count())
                    .divide(BigDecimal.valueOf(deltas.size()), 3, java.math.RoundingMode.HALF_UP).toString(),
            "meanBootstrap95", bootstrap.isEmpty() ? "нет данных" :
                bootstrap.get(24) + " … " + bootstrap.get(974),
            "averagePairedAdvantage", pairs.isEmpty() ? "нет данных" : pairs.stream()
                .map(p -> (BigDecimal) p.get("modelMinusRules")).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(pairs.size()), 2, java.math.RoundingMode.HALF_UP));
    }
}
