package ru.itis.diploma.experiment;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Controller @RequiredArgsConstructor
@RequestMapping("/admin/bot-experiments")
@PreAuthorize("hasAuthority('ADMIN')")
@ConditionalOnProperty(name = "experiment.enabled", havingValue = "true")
public class ExperimentController {
    private final ExperimentManager manager;
    private final ExperimentScenarios scenarios;
    private final ExperimentReports reports;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    @GetMapping
    public String page(Model model) {
        model.addAttribute("config", new ExperimentConfig());
        return "bot_experiments";
    }

    @GetMapping("/series") @ResponseBody
    public List<SeriesItem> series() {
        return manager.list().stream().map(s -> new SeriesItem(s.getId(), s.getStatus(), s.getMode()))
            .toList();
    }

    @GetMapping("/preview") @ResponseBody
    public List<Preview> preview(@ModelAttribute ExperimentConfig config) {
        config.validate();
        return java.util.stream.IntStream.rangeClosed(1, Math.min(5, config.getTarget())).mapToObj(i -> {
            var s = scenarios.generate(config, i);
            var chosen = java.util.stream.IntStream.range(0, s.bots())
                .mapToObj(bot -> bot == 0 && "EVALUATE".equals(config.getMode()) ?
                    (i % 2 == 1 ? "EVAL_RULES" : "MODEL") : config.policies()[bot % config.policies().length])
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
        var runs = manager.runs(id);
        long completed = runs.stream().filter(r -> "COMPLETED".equals(r.getStatus())).count();
        long elapsed = Math.max(0, java.time.Duration.between(series.getCreatedAt(),
            java.time.LocalDateTime.now()).getSeconds());
        Long etaSeconds = completed < 3 || !"RUNNING".equals(series.getStatus()) ? null :
            elapsed * Math.max(0, configTarget(series.getConfigJson()) - completed) / completed;
        return new Status(series.getStatus(), series.getConfigJson(), series.getModelVersion(),
            series.getGeneratorVersion(), series.getPolicyVersion(),
            completed,
            runs.stream().filter(r -> "RUNNING".equals(r.getStatus())).count(),
            runs.stream().filter(r -> "ERROR".equals(r.getStatus())).count(),
            runs.stream().filter(r -> "QUEUED".equals(r.getStatus())).count(),
            runs.stream().filter(r -> "INCOMPLETE".equals(r.getStatus())).count(),
            runs.stream().mapToLong(ExperimentRun::getCompletedDays).sum(), etaSeconds,
            runs.stream().limit(100).map(r -> new Run(r.getNumber(), r.getStatus(), Long.toString(r.getSeed()),
                r.getMarket(), r.getBots(), r.getHorizon(), r.getCompletedDays(), r.getCycles(),
                r.getBestResult() == null ? null : r.getBestResult().toString(), r.getError(),
                r.getModelDecisions(), r.getModelFallbacks(), r.getAttempts(),
                r.getStockSkips(), r.getCreditSkips())).toList());
    }

    private long configTarget(String json) {
        try { return mapper.readTree(json).path("target").asLong(); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { return 0; }
    }

    @GetMapping("/{id}/report") @ResponseBody
    public java.util.Map<String, Object> report(@PathVariable Long id) {
        manager.get(id);
        return reports.report(id);
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
    public record Run(int number, String status, String seed, String market, int bots, int horizon,
                      int days, int cycles, String bestResult, String error, int modelDecisions, int modelFallbacks,
                      int attempts, int stockSkips, int creditSkips) {}
    public record Status(String state, String config, String modelVersion, String generatorVersion, String policyVersion,
                         long completed, long active, long errors, long queued, long incomplete,
                         long processedDays, Long etaSeconds, List<Run> runs) {}
}
