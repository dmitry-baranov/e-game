package ru.itis.diploma.experiment.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed SQL boundary for denormalized analytics reads. */
@Repository
@RequiredArgsConstructor
public class BotAnalyticsReadRepository {
    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> series(String status, String mode, int offset) {
        return jdbc.queryForList("""
            SELECT es.id, es.status, es.mode, es.created_at, es.generator_version, es.policy_version,
                   es.model_version, (es.config_json::jsonb->>'target')::int AS target,
                   count(r.id) AS started,
                   count(r.id) FILTER (WHERE r.status='COMPLETED') AS completed,
                   count(r.id) FILTER (WHERE r.status='INCOMPLETE') AS incomplete,
                   count(r.id) FILTER (WHERE r.status='ERROR') AS errors,
                   coalesce(sum(r.cycles) FILTER (WHERE r.status='COMPLETED'),0) AS cycles
            FROM experiment_series es LEFT JOIN experiment_run r ON r.series_id=es.id
            WHERE (?='' OR es.status=?) AND (?='' OR es.mode=?)
            GROUP BY es.id ORDER BY es.id DESC LIMIT 50 OFFSET ?
            """, status, status, mode, mode, offset);
    }

    public long seriesCount(String status, String mode) {
        return jdbc.queryForObject("""
            SELECT count(*) FROM experiment_series WHERE (?='' OR status=?) AND (?='' OR mode=?)
            """, Long.class, status, status, mode, mode);
    }

    public List<Map<String, Object>> marketPolicies(long seriesId) {
        return jdbc.queryForList("""
            SELECT r.market, m.selected_strategy AS policy, count(*) AS players,
                   count(DISTINCT r.id) AS games,
                   percentile_disc(0.5) WITHIN GROUP (ORDER BY gr.result) AS median_result,
                   count(*) FILTER (WHERE gr.result>0) AS positive
            FROM experiment_run r JOIN manufacturer m ON m.game_id=r.game_id
            JOIN game_result gr ON gr.manufacturer_id=m.id
            WHERE r.series_id=? AND r.status='COMPLETED'
            GROUP BY r.market, m.selected_strategy ORDER BY r.market, m.selected_strategy
            """, seriesId);
    }

    public List<Map<String, Object>> trainingReports(long seriesId) {
        return jdbc.queryForList("""
            SELECT training_run_id::text AS id, status, created_at, report->>'champion' AS champion
            FROM training_run_report WHERE report->'series_ids' @> jsonb_build_array(cast(? AS text))
            ORDER BY created_at DESC LIMIT 30
            """, Long.toString(seriesId));
    }

    public List<Map<String, Object>> runs(long seriesId, String status, String market, int offset) {
        return jdbc.queryForList("""
            SELECT id, number, seed::text AS seed, status, market, bots, horizon, completed_days,
                   cycles, attempts, stock_skips, credit_skips, model_decisions, model_fallbacks, best_result
            FROM experiment_run WHERE series_id=? AND (?='' OR status=?) AND (?='' OR market=?)
            ORDER BY number LIMIT 50 OFFSET ?
            """, seriesId, status, status, market, market, offset);
    }

    public long runCount(long seriesId, String status, String market) {
        return jdbc.queryForObject("""
            SELECT count(*) FROM experiment_run WHERE series_id=? AND (?='' OR status=?) AND (?='' OR market=?)
            """, Long.class, seriesId, status, status, market, market);
    }

    public List<Map<String, Object>> players(long gameId) {
        return jdbc.queryForList("""
            SELECT m.id, a.full_name AS name, m.selected_strategy AS policy, m.investment_credit_amount,
                   m.production_capacity_per_day, m.current_product_count, m.balance,
                   gr.result AS result_after_debt,
                   (SELECT count(*) FROM production_parameters p WHERE p.manufacturer_id=m.id) AS cycles,
                   (SELECT coalesce(sum(o.products_sold),0) FROM cycle_observation o WHERE o.manufacturer_id=m.id) AS sold,
                   (SELECT coalesce(sum(o.products_produced),0) FROM cycle_observation o WHERE o.manufacturer_id=m.id) AS produced,
                   (SELECT count(*) FROM bot_decision_log d WHERE d.manufacturer_id=m.id AND d.outcome='ACCEPTED') AS accepted,
                   (SELECT count(*) FROM bot_decision_log d WHERE d.manufacturer_id=m.id AND d.outcome='STOCK_SKIP') AS stock_skips,
                   (SELECT count(*) FROM bot_decision_log d WHERE d.manufacturer_id=m.id AND d.outcome='CREDIT_SKIP') AS credit_skips
            FROM manufacturer m JOIN account a ON a.id=m.account_id
            LEFT JOIN game_result gr ON gr.manufacturer_id=m.id WHERE m.game_id=? ORDER BY m.id
            """, gameId);
    }

    public Map<String, Object> game(long gameId) {
        return jdbc.queryForMap("SELECT * FROM game WHERE id=?", gameId);
    }

    public List<Map<String, Object>> salesDays(long manufacturerId) {
        return jdbc.queryForList("""
            SELECT trade_date, products_sold FROM statistics_info
            WHERE manufacturer_id=? ORDER BY trade_date, id
            """, manufacturerId);
    }

    public List<Map<String, Object>> days(long manufacturerId) {
        return jdbc.queryForList("""
            SELECT trade_date, products_produced, products_sold, products_in_stock, price,
                   balance, paid_taxes_amount, repaid_investment_credit_amount, repaid_business_credit_amount,
                   current_investment_credit_debt_amount, current_business_credit_debt_amount
            FROM statistics_info WHERE manufacturer_id=? ORDER BY trade_date, id
            """, manufacturerId);
    }

    public List<Map<String, Object>> advertisements(long manufacturerId) {
        return jdbc.queryForList("""
            SELECT start_date,end_date,intensity_index,cost FROM advertisement
            WHERE manufacturer_id=? ORDER BY start_date
            """, manufacturerId);
    }

    public List<Map<String, Object>> investmentPayments(long manufacturerId) {
        return jdbc.queryForList("""
            SELECT date, principal_payment, interest_amount, next_date
            FROM investment_credit_payment WHERE manufacturer_id=? ORDER BY date,id
            """, manufacturerId);
    }

    public List<Map<String, Object>> businessPayments(long manufacturerId) {
        return jdbc.queryForList("""
            SELECT b.date, b.amount, b.next_date, b.next_amount, p.start_date AS cycle_day
            FROM business_credit_payment b JOIN production_parameters p ON p.id=b.production_parameters_id
            WHERE p.manufacturer_id=? ORDER BY b.date,b.id
            """, manufacturerId);
    }

    public List<Map<String, Object>> taxes(long manufacturerId) {
        return jdbc.queryForList("SELECT date, amount FROM sales_tax_payment WHERE manufacturer_id=? ORDER BY date,id",
            manufacturerId);
    }

    public List<Map<String, Object>> trades(long manufacturerId) {
        return jdbc.queryForList("""
            SELECT trade_date, product_number, price, quality_index, is_worn_out
            FROM trading_session_results WHERE manufacturer_id=? ORDER BY trade_date,id LIMIT 1000
            """, manufacturerId);
    }

    public List<Map<String, Object>> decisions(long runId, long manufacturerId) {
        return jdbc.queryForList("""
            SELECT decision_day, policy, model_version, outcome, used_model, fallback, fallback_reason,
                   recommendation_source, snapshot_json, proposed_action_json, candidates_json
            FROM bot_decision_log WHERE run_id=? AND manufacturer_id=? ORDER BY decision_day,id
            """, runId, manufacturerId);
    }

    public List<Map<String, Object>> episodes(long manufacturerId) {
        return jdbc.queryForList("""
            SELECT e.decision_day, e.cycle_days, e.snapshot_json, e.action_json,
                   coalesce(o.observed_days,0) AS observed_days, 0::bigint AS duplicate_days,
                   o.products_sold AS sold
            FROM strategy_training_episode e
            LEFT JOIN cycle_observation o ON o.production_parameters_id=e.cycle_id OR
                 (e.cycle_id IS NULL AND o.manufacturer_id=e.manufacturer_id AND o.start_date=e.decision_day)
            WHERE e.manufacturer_id=? ORDER BY e.decision_day,e.id
            """, manufacturerId);
    }

    public List<Map<String, Object>> trainingModel(String modelVersion) {
        return jdbc.queryForList("""
            SELECT training_run_id::text AS id FROM training_run_report
            WHERE report->>'version'=? AND status='trained' ORDER BY created_at LIMIT 1
            """, modelVersion);
    }

    public boolean hasTrainingReports() {
        return jdbc.queryForObject("SELECT to_regclass('public.training_run_report')::text", String.class) != null;
    }

    public List<CycleRow> cycles(long manufacturerId) {
        return jdbc.query("""
            SELECT o.start_date, o.expected_days, p.product_count, p.production_capacity_per_day,
                   p.price, p.cost_price, p.assortment, p.quality_index, p.business_credit_amount,
                   o.observed_days, o.products_sold, o.products_produced, o.revenue, o.payments,
                   o.ending_stock, o.balance_change, o.complete
            FROM cycle_observation o JOIN production_parameters p ON p.id=o.production_parameters_id
            WHERE o.manufacturer_id=? ORDER BY o.start_date,p.id
            """, (rs, row) -> new CycleRow(rs.getInt("start_date"), rs.getInt("expected_days"),
            (Integer) rs.getObject("product_count"), (Integer) rs.getObject("production_capacity_per_day"),
            rs.getBigDecimal("price"), rs.getBigDecimal("cost_price"), (Integer) rs.getObject("assortment"),
            rs.getBigDecimal("quality_index"), rs.getBigDecimal("business_credit_amount"),
            rs.getInt("observed_days"), rs.getInt("products_sold"), rs.getInt("products_produced"),
            rs.getBigDecimal("revenue"), rs.getBigDecimal("payments"), (Integer) rs.getObject("ending_stock"),
            rs.getBigDecimal("balance_change"), rs.getBoolean("complete")), manufacturerId);
    }

    public Coverage coverage(long seriesId) {
        return jdbc.queryForObject("""
            SELECT count(e.id) AS episodes,
                   count(e.id) FILTER (WHERE o.complete) AS complete_cycles,
                   count(e.id) FILTER (WHERE o.id IS NULL OR NOT o.complete) AS incomplete_cycles,
                   0::bigint AS duplicate_days,
                   count(e.id) FILTER (WHERE o.complete AND o.products_sold=0) AS zero_sales_cycles
            FROM strategy_training_episode e
            JOIN manufacturer m ON m.id=e.manufacturer_id
            JOIN experiment_run r ON r.game_id=m.game_id
            LEFT JOIN cycle_observation o ON o.production_parameters_id=e.cycle_id OR
                 (e.cycle_id IS NULL AND o.manufacturer_id=e.manufacturer_id AND o.start_date=e.decision_day)
            WHERE r.series_id=? AND r.status='COMPLETED'
            """, (rs, row) -> new Coverage(rs.getLong("episodes"), rs.getLong("complete_cycles"),
            rs.getLong("incomplete_cycles"), rs.getLong("duplicate_days"), rs.getLong("zero_sales_cycles")), seriesId);
    }

    public record Coverage(long episodes, long completeCycles, long incompleteCycles,
                           long duplicateDays, long zeroSalesCycles) {
        public Map<String, Object> templateModel() {
            return Map.of("episodes", episodes, "complete_cycles", completeCycles,
                "incomplete_cycles", incompleteCycles, "duplicate_days", duplicateDays,
                "zero_sales_cycles", zeroSalesCycles);
        }
    }

    public record CycleRow(int startDate, int timeToMarket, Integer productCount,
                           Integer productionCapacityPerDay, BigDecimal price, BigDecimal costPrice,
                           Integer assortment, BigDecimal qualityIndex, BigDecimal businessCreditAmount,
                           int observedDays, int observedSold, int observedProduced, BigDecimal observedRevenue,
                           BigDecimal observedPayments, Integer endingStock, BigDecimal balanceChange,
                           boolean complete) {
        public Map<String, Object> templateModel() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("start_date", startDate); row.put("time_to_market", timeToMarket);
            row.put("product_count", productCount); row.put("production_capacity_per_day", productionCapacityPerDay);
            row.put("price", price); row.put("cost_price", costPrice); row.put("assortment", assortment);
            row.put("quality_index", qualityIndex); row.put("business_credit_amount", businessCreditAmount);
            row.put("observed_days", observedDays); row.put("observed_sold", observedSold);
            row.put("observed_produced", observedProduced); row.put("observed_revenue", observedRevenue);
            row.put("observed_payments", observedPayments); row.put("ending_stock", endingStock);
            row.put("balance_change", balanceChange); row.put("complete", complete);
            return row;
        }
    }
}
