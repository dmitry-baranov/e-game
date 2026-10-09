package ru.itis.diploma.experiment;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Controller @RequiredArgsConstructor
@RequestMapping("/admin/bot-analytics")
@PreAuthorize("hasAuthority('ADMIN')")
@ConditionalOnProperty(name = "experiment.enabled", havingValue = "true")
public class BotAnalyticsController {
    private final JdbcTemplate jdbc;
    private final ExperimentRunRepository runs;
    private final ExperimentSeriesRepository series;
    private final ExperimentReports reports;
    private final ExperimentManager manager;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("title", "Аналитика ботов и моделей");
        model.addAttribute("series", jdbc.queryForList("""
            SELECT es.id, es.status, es.mode, es.created_at, es.generator_version, es.policy_version,
                   es.model_version, count(r.id) AS started,
                   count(r.id) FILTER (WHERE r.status='COMPLETED') AS completed,
                   count(r.id) FILTER (WHERE r.status='INCOMPLETE') AS incomplete,
                   count(r.id) FILTER (WHERE r.status='ERROR') AS errors,
                   coalesce(sum(r.cycles) FILTER (WHERE r.status='COMPLETED'),0) AS cycles
            FROM experiment_series es LEFT JOIN experiment_run r ON r.series_id=es.id
            GROUP BY es.id ORDER BY es.id DESC LIMIT 100
            """));
        return "bot_analytics";
    }

    @GetMapping("/series/{id}")
    public String series(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "") String status,
                         @RequestParam(defaultValue = "") String market, Model model) {
        var group = series.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (page < 0 || page > 100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (!List.of("", "COMPLETED", "INCOMPLETE", "ERROR", "CANCELLED", "RUNNING", "QUEUED", "WAITING_PAIR").contains(status) ||
            !List.of("", "Бюджетный", "Конкурентный", "Дорогая реклама").contains(market))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        model.addAttribute("title", "Серия #" + id);
        model.addAttribute("group", group);
        model.addAttribute("coverage", reports.report(id));
        model.addAttribute("training", hasTrainingReports() ? jdbc.queryForList("""
            SELECT training_run_id::text AS id, status, created_at, report->>'champion' AS champion
            FROM training_run_report WHERE report->'series_ids' @> jsonb_build_array(cast(? AS text))
            ORDER BY created_at DESC LIMIT 30
            """, id.toString()) : List.of());
        model.addAttribute("games", jdbc.queryForList("""
            SELECT id, number, seed::text AS seed, status, market, bots, horizon, completed_days,
                   cycles, attempts, stock_skips, credit_skips, model_decisions, model_fallbacks, best_result
            FROM experiment_run WHERE series_id=? AND (?='' OR status=?) AND (?='' OR market=?)
            ORDER BY number LIMIT 50 OFFSET ?
            """, id, status, status, market, market, page * 50));
        model.addAttribute("page", page);
        long count = jdbc.queryForObject("""
            SELECT count(*) FROM experiment_run WHERE series_id=? AND (?='' OR status=?) AND (?='' OR market=?)
            """, Long.class, id, status, status, market, market);
        model.addAttribute("count", count);
        model.addAttribute("statusFilter", status);
        model.addAttribute("marketFilter", market);
        model.addAttribute("hasNext", count > (long) (page + 1) * 50);
        return "bot_analytics_series";
    }

    @GetMapping("/runs/{id}")
    public String run(@PathVariable Long id, @RequestParam(required = false) Long bot,
                      @RequestParam(defaultValue = "series") String from,
                      @RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = "") String status,
                      @RequestParam(defaultValue = "") String market, Model model) {
        var run = runs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (run.getGame() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!List.of("series", "evaluation", "game", "status").contains(from) || page < 0 || page > 100000 ||
            !List.of("", "COMPLETED", "INCOMPLETE", "ERROR", "CANCELLED", "RUNNING", "QUEUED", "WAITING_PAIR").contains(status) ||
            !List.of("", "Бюджетный", "Конкурентный", "Дорогая реклама").contains(market) ||
            ("evaluation".equals(from) && !"EVALUATE".equals(run.getSeries().getMode())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        Long game = run.getGame().getId();
        String seriesUrl = UriComponentsBuilder.fromPath("/admin/bot-analytics/series/{id}")
            .queryParam("page", page).queryParam("status", status).queryParam("market", market)
            .buildAndExpand(run.getSeries().getId()).encode().toUriString();
        String backUrl = switch (from) {
            case "evaluation" -> "/admin/bot-analytics/evaluations/" + run.getSeries().getId();
            case "game" -> "/game/" + game;
            case "status" -> "/admin/bot-experiments/" + run.getSeries().getId();
            default -> seriesUrl;
        };
        model.addAttribute("backUrl", backUrl);
        model.addAttribute("backLabel", switch (from) {
            case "evaluation" -> "К парной оценке";
            case "game" -> "К странице игры";
            case "status" -> "К управлению серией";
            default -> "К списку игр серии";
        });
        model.addAttribute("seriesUrl", seriesUrl);
        model.addAttribute("botSelectionUrl", UriComponentsBuilder.fromPath("/admin/bot-analytics/runs/{id}")
            .queryParam("from", from).queryParam("page", page).queryParam("status", status)
            .queryParam("market", market).buildAndExpand(id).encode().toUriString());
        var players = jdbc.queryForList("""
            SELECT m.id, a.full_name AS name, m.selected_strategy AS policy, m.investment_credit_amount,
                   m.production_capacity_per_day, m.current_product_count,
                   gr.result AS result_after_debt,
                   (SELECT count(*) FROM production_parameters p WHERE p.manufacturer_id=m.id) AS cycles,
                   (SELECT coalesce(sum(s.products_sold),0) FROM statistics_info s WHERE s.manufacturer_id=m.id) AS sold
            FROM manufacturer m JOIN account a ON a.id=m.account_id
            LEFT JOIN game_result gr ON gr.manufacturer_id=m.id WHERE m.game_id=? ORDER BY m.id
            """, game);
        if (bot == null && !players.isEmpty()) bot = ((Number) players.get(0).get("id")).longValue();
        if (bot != null) {
            Long selectedBot = bot;
            if (players.stream().noneMatch(p -> ((Number) p.get("id")).longValue() == selectedBot.longValue()))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("title", "Игра #" + run.getNumber());
        model.addAttribute("run", run);
        model.addAttribute("game", jdbc.queryForMap("SELECT * FROM game WHERE id=?", game));
        model.addAttribute("players", players);
        model.addAttribute("bot", bot);
        if (bot != null) {
            model.addAttribute("days", jdbc.queryForList("""
                SELECT trade_date, products_produced, products_sold, products_in_stock, price,
                       balance, paid_taxes_amount, repaid_investment_credit_amount, repaid_business_credit_amount,
                       current_investment_credit_debt_amount, current_business_credit_debt_amount
                FROM statistics_info WHERE manufacturer_id=? ORDER BY trade_date, id
                """, bot));
            model.addAttribute("cycles", jdbc.queryForList("""
                SELECT start_date, time_to_market, product_count, production_capacity_per_day,
                       price, cost_price, assortment, quality_index, business_credit_amount
                FROM production_parameters WHERE manufacturer_id=? ORDER BY start_date, id
                """, bot));
            model.addAttribute("ads", jdbc.queryForList("SELECT start_date,end_date,intensity_index,cost FROM advertisement WHERE manufacturer_id=? ORDER BY start_date", bot));
            model.addAttribute("investmentPayments", jdbc.queryForList("""
                SELECT date, principal_payment, interest_amount, next_date
                FROM investment_credit_payment WHERE manufacturer_id=? ORDER BY date,id
                """, bot));
            model.addAttribute("businessPayments", jdbc.queryForList("""
                SELECT b.date, b.amount, b.next_date, b.next_amount, p.start_date AS cycle_day
                FROM business_credit_payment b JOIN production_parameters p ON p.id=b.production_parameters_id
                WHERE p.manufacturer_id=? ORDER BY b.date,b.id
                """, bot));
            model.addAttribute("taxes", jdbc.queryForList("SELECT date, amount FROM sales_tax_payment WHERE manufacturer_id=? ORDER BY date,id", bot));
            model.addAttribute("trades", jdbc.queryForList("""
                SELECT trade_date, product_number, price, quality_index, is_worn_out
                FROM trading_session_results WHERE manufacturer_id=? ORDER BY trade_date,id LIMIT 1000
                """, bot));
            model.addAttribute("decisions", jdbc.queryForList("""
                SELECT decision_day, policy, model_version, outcome, used_model, fallback, fallback_reason,
                       recommendation_source, snapshot_json, proposed_action_json, candidates_json
                FROM bot_decision_log WHERE run_id=? AND manufacturer_id=? ORDER BY decision_day,id
                """, id, bot));
            model.addAttribute("episodes", jdbc.queryForList("""
                SELECT decision_day, cycle_days, snapshot_json, action_json,
                        (SELECT count(DISTINCT s.trade_date) FROM statistics_info s
                         WHERE s.manufacturer_id=e.manufacturer_id AND s.trade_date>e.decision_day
                           AND s.trade_date<=e.decision_day+e.cycle_days) AS observed_days,
                        (SELECT count(s.id)-count(DISTINCT s.trade_date) FROM statistics_info s
                         WHERE s.manufacturer_id=e.manufacturer_id AND s.trade_date>e.decision_day
                           AND s.trade_date<=e.decision_day+e.cycle_days) AS duplicate_days,
                       (SELECT sum(s.products_sold) FROM statistics_info s
                        WHERE s.manufacturer_id=e.manufacturer_id AND s.trade_date>e.decision_day
                          AND s.trade_date<=e.decision_day+e.cycle_days) AS sold
                FROM strategy_training_episode e WHERE manufacturer_id=? ORDER BY decision_day,id
                """, bot));
        }
        return "bot_analytics_run";
    }

    @GetMapping("/evaluations/{id}")
    public String evaluation(@PathVariable Long id, Model model) {
        var group = series.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!"EVALUATE".equals(group.getMode())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("title", "Оценка серии #" + id);
        model.addAttribute("group", group);
        model.addAttribute("report", reports.report(id));
        model.addAttribute("trainingId", hasTrainingReports() && group.getModelVersion() != null ?
            jdbc.queryForList("""
                SELECT training_run_id::text AS id FROM training_run_report
                WHERE report->>'version'=? AND status='trained' ORDER BY created_at LIMIT 1
                """, group.getModelVersion()) : List.of());
        return "bot_analytics_evaluation";
    }

    private boolean hasTrainingReports() {
        return jdbc.queryForObject("SELECT to_regclass('public.training_run_report')::text", String.class) != null;
    }

    @GetMapping("/training-report") @ResponseBody
    public com.fasterxml.jackson.databind.JsonNode trainingReport() { return manager.trainingReport(); }

    @GetMapping("/training")
    public String training(Model model) {
        model.addAttribute("runs", mapper.convertValue(manager.trainingRuns(), List.class));
        return "bot_analytics_training";
    }

    @GetMapping("/training/{id}")
    public String training(@PathVariable String id, @RequestParam(defaultValue = "training") String from,
                           @RequestParam(required = false) Long series, Model model) {
        if (!List.of("training", "series", "evaluation").contains(from))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var report = manager.trainingRun(id);
        if (!"training".equals(from)) {
            var origin = series == null ? null : this.series.findById(series).orElse(null);
            if (origin == null || ("evaluation".equals(from) && !"EVALUATE".equals(origin.getMode())) ||
                ("series".equals(from) && !java.util.stream.StreamSupport.stream(
                    report.path("series_ids").spliterator(), false).anyMatch(item -> item.asText().equals(series.toString()))))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        model.addAttribute("backUrl", switch (from) {
            case "series" -> "/admin/bot-analytics/series/" + series;
            case "evaluation" -> "/admin/bot-analytics/evaluations/" + series;
            default -> "/admin/bot-analytics/training";
        });
        model.addAttribute("backLabel", switch (from) {
            case "series" -> "К серии";
            case "evaluation" -> "К парной оценке";
            default -> "К истории обучения";
        });
        model.addAttribute("report", mapper.convertValue(report, Map.class));
        return "bot_analytics_training_run";
    }

    @GetMapping("/training/{id}/json") @ResponseBody
    public com.fasterxml.jackson.databind.JsonNode trainingJson(@PathVariable String id) {
        return manager.trainingRun(id);
    }
}
