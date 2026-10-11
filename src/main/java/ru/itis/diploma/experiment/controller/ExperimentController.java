package ru.itis.diploma.experiment.controller;

import ru.itis.diploma.experiment.config.ExperimentConfig;
import ru.itis.diploma.experiment.service.ExperimentManager;
import ru.itis.diploma.experiment.service.ExperimentScenarios;

import ru.itis.diploma.experiment.bot.BotPolicies;
import ru.itis.diploma.experiment.bot.BotParticipants;
import ru.itis.diploma.experiment.model.ExperimentRun;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Администраторский интерфейс управления сериями. Контроллер существует только
 * в экспериментальном приложении; создание и изменение состояния идут через
 * ExperimentManager, а частый JSON-опрос статуса читает лёгкие счётчики и 30
 * последних попыток. Полный статистический отчёт читается отдельно по требованию.
 */
@Controller @RequiredArgsConstructor
@RequestMapping("/admin/bot-experiments")
@PreAuthorize("hasAuthority('ADMIN')")
@ConditionalOnProperty(name = "experiment.enabled", havingValue = "true")
public class ExperimentController {
    private final ExperimentManager manager;
    private final ExperimentScenarios scenarios;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @GetMapping
    public String page(Model model) {
        model.addAttribute("config", new ExperimentConfig());
        model.addAttribute("policyNames", BotPolicies.NAMES);
        model.addAttribute("policyDescriptions", BotPolicies.DESCRIPTIONS);
        model.addAttribute("markets", ExperimentScenarios.MARKETS);
        model.addAttribute("marketNames", ExperimentScenarios.MARKET_NAMES);
        return "bot_experiments";
    }

    @GetMapping("/series") @ResponseBody
    public List<SeriesItem> series() {
        return jdbc.query("SELECT id, status, mode FROM experiment_series ORDER BY id DESC LIMIT 20",
            (rs, row) -> new SeriesItem(rs.getLong("id"), rs.getString("status"), rs.getString("mode")));
    }

    @GetMapping("/preview") @ResponseBody
    public List<Preview> preview(@ModelAttribute ExperimentConfig config) {
        config.validate();
        return java.util.stream.IntStream.rangeClosed(1, Math.min(5, config.getTarget())).mapToObj(i -> {
            var s = scenarios.generate(config, i);
            var chosen = java.util.stream.IntStream.range(0, s.bots())
                .mapToObj(bot -> BotParticipants.policyFor(config.getMode(), config.policies(), i, bot))
                .collect(java.util.stream.Collectors.groupingBy(p -> p, java.util.LinkedHashMap::new,
                    java.util.stream.Collectors.counting()));
            return new Preview(i, Long.toString(s.seed()), s.market(), s.bots(), s.horizon(),
                s.game().getBaseCostPrice().toString(), s.game().getPurchaseLimit(),
                s.game().getDailySpendingLimit().toString(), chosen.toString());
        }).toList();
    }

    @PostMapping
    public String create(@ModelAttribute ExperimentConfig config, Model model) {
        try { return "redirect:/admin/bot-experiments/" + manager.create(config).getId(); }
        catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage()); model.addAttribute("config", config);
            model.addAttribute("policyNames", BotPolicies.NAMES);
            model.addAttribute("policyDescriptions", BotPolicies.DESCRIPTIONS);
            model.addAttribute("markets", ExperimentScenarios.MARKETS);
            model.addAttribute("marketNames", ExperimentScenarios.MARKET_NAMES);
            return "bot_experiments";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, @RequestParam(required = false) String error, Model model) {
        model.addAttribute("seriesId", manager.get(id).getId());
        if (error != null) model.addAttribute("error", error);
        return "bot_experiment_status";
    }

    @GetMapping("/{id}/status") @ResponseBody
    public Status status(@PathVariable Long id) {
        var series = manager.get(id);
        // Частый опрос интерфейса читает агрегат и только последние игры, а не всю историю серии.
        var counts = jdbc.queryForMap("""
            SELECT count(*) FILTER (WHERE status='COMPLETED') AS completed,
                   count(*) FILTER (WHERE status='RUNNING') AS active,
                   count(*) FILTER (WHERE status='ERROR') AS errors,
                   count(*) FILTER (WHERE status='QUEUED') AS queued,
                   count(*) FILTER (WHERE status='INCOMPLETE') AS incomplete,
                   coalesce(sum(completed_days),0) AS days
            FROM experiment_run WHERE series_id=?
            """, id);
        long completed = ((Number) counts.get("completed")).longValue();
        long elapsed = Math.max(0, java.time.Duration.between(series.getCreatedAt(),
            java.time.LocalDateTime.now()).getSeconds());
        Long etaSeconds = completed < 3 || !"RUNNING".equals(series.getStatus()) ? null :
            elapsed * Math.max(0, configTarget(series.getConfigJson()) - completed) / completed;
        return new Status(series.getStatus(), series.getConfigJson(), series.getModelVersion(),
            series.getGeneratorVersion(), series.getPolicyVersion(),
            completed,
            ((Number) counts.get("active")).longValue(), ((Number) counts.get("errors")).longValue(),
            ((Number) counts.get("queued")).longValue(), ((Number) counts.get("incomplete")).longValue(),
            ((Number) counts.get("days")).longValue(), etaSeconds,
            jdbc.query("""
                SELECT id, number, status, seed::text AS seed, market, bots, horizon, completed_days,
                       cycles, best_result, error, model_decisions, model_fallbacks, attempts, stock_skips, credit_skips
                FROM experiment_run WHERE series_id=? ORDER BY number DESC LIMIT 30
                """, (rs, row) -> new Run(rs.getLong("id"), rs.getInt("number"), rs.getString("status"),
                    rs.getString("seed"), rs.getString("market"), rs.getInt("bots"), rs.getInt("horizon"),
                    rs.getInt("completed_days"), rs.getInt("cycles"),
                    rs.getBigDecimal("best_result") == null ? null : rs.getBigDecimal("best_result").toString(),
                    rs.getString("error"), rs.getInt("model_decisions"), rs.getInt("model_fallbacks"),
                    rs.getInt("attempts"), rs.getInt("stock_skips"), rs.getInt("credit_skips")), id));
    }

    private long configTarget(String json) {
        try { return mapper.readTree(json).path("target").asLong(); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { return 0; }
    }

    @GetMapping("/training-report") @ResponseBody
    public com.fasterxml.jackson.databind.JsonNode trainingReport() { return manager.trainingReport(); }

    @PostMapping("/{id}/train")
    public String train(@PathVariable Long id, org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        try { manager.train(id); }
        catch (IllegalArgumentException e) { redirect.addAttribute("error", e.getMessage()); }
        return "redirect:/admin/bot-experiments/" + id;
    }

    @PostMapping("/{id}/{operation}")
    public String change(@PathVariable Long id, @PathVariable String operation) {
        if (!List.of("pause", "resume", "stop").contains(operation))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        manager.change(id, operation);
        return "redirect:/admin/bot-experiments/" + id;
    }

    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) @ResponseBody
    public String invalid(IllegalArgumentException error) { return error.getMessage(); }

    public record Preview(int number, String seed, String market, int bots, int horizon,
                          String baseCost, int purchaseLimit, String dailyBudget, String policies) {}
    public record SeriesItem(long id, String status, String mode) {}
    public record Run(long id, int number, String status, String seed, String market, int bots, int horizon,
                      int days, int cycles, String bestResult, String error, int modelDecisions, int modelFallbacks,
                      int attempts, int stockSkips, int creditSkips) {}
    public record Status(String state, String config, String modelVersion, String generatorVersion, String policyVersion,
                         long completed, long active, long errors, long queued, long incomplete,
                         long processedDays, Long etaSeconds, List<Run> runs) {}
}
