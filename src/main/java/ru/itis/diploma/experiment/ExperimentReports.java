package ru.itis.diploma.experiment;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor
public class ExperimentReports {
    private final JdbcTemplate jdbc;

    public Map<String, Object> report(long seriesId) {
        var coverage = jdbc.queryForMap("""
            SELECT count(*) AS episodes, count(*) FILTER (WHERE observed=cycle_days) AS complete_cycles,
                   count(*) FILTER (WHERE observed<cycle_days) AS incomplete_cycles,
                   count(*) FILTER (WHERE sold=0) AS zero_sales_cycles
            FROM (SELECT e.id, e.cycle_days, count(s.id) AS observed,
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
                   round(avg(gr.result / nullif(g.current_day,0)),2) AS mean_result_per_day,
                   round(avg(m.current_product_count),2) AS mean_stock
            FROM experiment_run r JOIN game g ON g.id=r.game_id
            JOIN manufacturer m ON m.game_id=g.id
            JOIN game_result gr ON gr.manufacturer_id=m.id
            WHERE r.series_id=? AND r.status='COMPLETED'
            GROUP BY m.selected_strategy ORDER BY m.selected_strategy
            """, seriesId);
        var rows = jdbc.queryForList("""
            SELECT r.number, r.seed, gr.result, g.current_day AS days
            FROM experiment_run r JOIN game g ON g.id=r.game_id
            JOIN manufacturer m ON m.game_id=g.id
            JOIN game_result gr ON gr.manufacturer_id=m.id
            WHERE r.series_id=? AND r.status='COMPLETED' AND m.id=(
                SELECT min(m2.id) FROM manufacturer m2 WHERE m2.game_id=g.id)
            ORDER BY r.number
            """, seriesId);
        List<Map<String, Object>> pairs = new ArrayList<>();
        Map<Integer, Map<String, Object>> first = new HashMap<>();
        for (var row : rows) {
            int number = ((Number) row.get("number")).intValue();
            if (number % 2 == 1) first.put(number, row);
            else if (first.containsKey(number - 1) &&
                Objects.equals(first.get(number - 1).get("seed"), row.get("seed"))) {
                var baseline = first.get(number - 1);
                BigDecimal delta = ((BigDecimal) row.get("result")).subtract((BigDecimal) baseline.get("result"));
                pairs.add(Map.of("pair", number / 2, "seed", row.get("seed"),
                    "rules", baseline.get("result"), "model", row.get("result"),
                    "modelMinusRules", delta, "days", row.get("days")));
            }
        }
        return Map.of("coverage", coverage, "policies", policies, "pairs", pairs,
            "averagePairedAdvantage", pairs.stream().map(p -> (BigDecimal) p.get("modelMinusRules"))
                .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(Math.max(1, pairs.size())),
                    2, java.math.RoundingMode.HALF_UP));
    }
}
