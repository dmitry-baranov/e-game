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
    private final BotAnalyticsReadRepository analytics;

    /**
     * Строит полный отчёт серии по завершённым играм и оценочным парам,
     * для которых созданы обе попытки.
     * Построение выполняется по запросу, а не при каждом обновлении статуса.
     *
     * @param seriesId идентификатор серии экспериментов
     * @return модель отчёта с покрытием, статистикой политик и результатами парного сравнения
     */
    public Map<String, Object> report(long seriesId) {
        var coverage = analytics.coverage(seriesId).templateModel();
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
        List<Map<String, Object>> pairs = completedPairs(rows);
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
        List<Map<String, Object>> allPairs = allPairs(allRuns);
        Map<String, Object> comparison = comparePairs(pairs);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("coverage", coverage);
        report.put("policies", policies);
        report.put("pairs", pairs);
        report.put("fallbackReasons", fallbackReasons);
        report.put("allPairs", allPairs);
        report.putAll(comparison);
        return Collections.unmodifiableMap(report);
    }

    /** Сравнение только завершённых половин с одинаковыми seed и рынком. */
    private List<Map<String, Object>> completedPairs(List<Map<String, Object>> rows) {
        List<Map<String, Object>> pairs = new ArrayList<>();
        Map<Integer, Map<String, Object>> first = new HashMap<>();
        for (Map<String, Object> row : rows) {
            int number = ((Number) row.get("number")).intValue();
            if (number % 2 == 1) {
                first.put(number, row);
                continue;
            }
            Map<String, Object> baseline = first.get(number - 1);
            if (baseline == null || !Objects.equals(baseline.get("seed"), row.get("seed"))
                || !Objects.equals(baseline.get("market"), row.get("market"))) {
                continue;
            }

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
        return pairs;
    }

    /** Все пары, включая незавершённые: показываем их статусы без расчёта эффекта. */
    private List<Map<String, Object>> allPairs(List<Map<String, Object>> runs) {
        List<Map<String, Object>> pairs = new ArrayList<>();
        Map<Integer, Map<String, Object>> odd = new HashMap<>();
        for (Map<String, Object> run : runs) {
            int number = ((Number) run.get("number")).intValue();
            if (number % 2 == 1) {
                odd.put(number, run);
                continue;
            }
            Map<String, Object> baseline = odd.get(number - 1);
            if (baseline == null) {
                continue;
            }
            Map<String, Object> pair = new LinkedHashMap<>();
            pair.put("pair", number / 2);
            pair.put("seed", run.get("seed"));
            pair.put("market", run.get("market"));
            pair.put("rulesRunId", baseline.get("id"));
            pair.put("modelRunId", run.get("id"));
            pair.put("rulesStatus", baseline.get("status"));
            pair.put("modelStatus", run.get("status"));
            pair.put("modelDecisions", run.get("model_decisions"));
            pair.put("modelFallbacks", run.get("model_fallbacks"));
            boolean paired = Objects.equals(baseline.get("seed"), run.get("seed"))
                && Objects.equals(baseline.get("market"), run.get("market"));
            pair.put("paired", paired);
            pairs.add(pair);
        }
        return pairs;
    }

    /** Медиана, доля побед и доверительный интервал по разницам «модель минус правила». */
    private Map<String, Object> comparePairs(List<Map<String, Object>> pairs) {
        List<BigDecimal> deltas = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        long wins = 0;
        for (Map<String, Object> pair : pairs) {
            BigDecimal delta = (BigDecimal) pair.get("modelMinusRules");
            deltas.add(delta);
            total = total.add(delta);
            if (delta.signum() > 0) {
                wins++;
            }
        }
        deltas.sort(Comparator.naturalOrder());

        Object median = "нет данных";
        Object winShare = "нет данных";
        Object average = "нет данных";
        if (!deltas.isEmpty()) {
            int middle = deltas.size() / 2;
            BigDecimal middleValue = deltas.get(middle);
            if (deltas.size() % 2 == 0) {
                middleValue = deltas.get(middle - 1).add(middleValue)
                    .divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
            }
            median = middleValue.toString();
            winShare = BigDecimal.valueOf(wins)
                .divide(BigDecimal.valueOf(deltas.size()), 3, java.math.RoundingMode.HALF_UP).toString();
            average = total.divide(BigDecimal.valueOf(pairs.size()), 2, java.math.RoundingMode.HALF_UP);
        }

        Object bootstrap95 = "нет данных";
        if (deltas.size() >= 2) {
            var random = new Random(42);
            List<BigDecimal> samples = new ArrayList<>();
            for (int sample = 0; sample < 1000; sample++) {
                BigDecimal sum = BigDecimal.ZERO;
                for (int i = 0; i < deltas.size(); i++) {
                    sum = sum.add(deltas.get(random.nextInt(deltas.size())));
                }
                samples.add(sum.divide(BigDecimal.valueOf(deltas.size()), 2, java.math.RoundingMode.HALF_UP));
            }
            samples.sort(Comparator.naturalOrder());
            bootstrap95 = samples.get(24) + " … " + samples.get(974);
        }

        return Map.of("medianPairedAdvantage", median, "modelWinShare", winShare,
            "meanBootstrap95", bootstrap95, "averagePairedAdvantage", average);
    }
}
