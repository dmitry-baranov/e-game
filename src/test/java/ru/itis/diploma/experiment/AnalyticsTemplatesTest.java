package ru.itis.diploma.experiment;

import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import freemarker.core.HTMLOutputFormat;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalyticsTemplatesTest {
    private final Configuration templates = new Configuration(Configuration.VERSION_2_3_31);

    AnalyticsTemplatesTest() {
        templates.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
        templates.setOutputFormat(HTMLOutputFormat.INSTANCE);
        templates.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
    }

    private String render(String name, Map<String, Object> model) throws Exception {
        var result = new StringWriter();
        templates.getTemplate(name + ".ftlh").process(model, result);
        return result.toString();
    }

    @Test
    void rendersEmptyAnalyticsWithoutInventedMeasurements() throws Exception {
        assertTrue(render("bot_experiments", Map.of("config", new ExperimentConfig(),
            "policyNames", BotPolicies.NAMES, "policyDescriptions", BotPolicies.DESCRIPTIONS))
            .contains("Серия автономных игр"));
        assertTrue(render("bot_experiment_status", Map.of("seriesId", 13L)).contains("серией #13"));
        assertTrue(render("bot_analytics", Map.of("title", "Аналитика", "series", List.of())).contains("Аналитика"));
        var legacySeries = new HashMap<String, Object>();
        legacySeries.put("id", 1L);
        legacySeries.put("status", "DONE");
        for (var key : List.of("started", "completed", "incomplete", "errors", "cycles")) legacySeries.put(key, 0);
        assertTrue(render("bot_analytics", Map.of("title", "Аналитика", "series", List.of(legacySeries)))
            .contains("нет данных"));
        var series = new ExperimentSeries();
        series.setId(1L);
        assertTrue(render("bot_analytics_series", Map.of("title", "Серия", "group", series,
            "coverage", Map.of("coverage", Map.of("episodes", 0, "complete_cycles", 0,
                "incomplete_cycles", 0, "duplicate_days", 0, "zero_sales_cycles", 0), "policies", List.of()),
            "games", List.of(), "training", List.of(), "page", 0, "hasNext", false,
            "count", 0, "statusFilter", "", "marketFilter", "")).contains("полных циклов без дублей: 0"));
        assertTrue(render("bot_analytics_training", Map.of("runs", List.of())).contains("История запусков"));
        assertTrue(render("bot_analytics_training_run", Map.of("report", Map.of(
            "training_run_id", "run", "status", "insufficient_data", "created_at", "now"),
            "backUrl", "/admin/bot-analytics/training", "backLabel", "К истории обучения"))
            .contains("нет данных"));
        assertTrue(render("bot_analytics_evaluation", Map.of("title", "Оценка", "group", series,
            "trainingId", List.of(), "report", Map.of("pairs", List.of(), "allPairs", List.of(),
                "averagePairedAdvantage", "нет данных", "medianPairedAdvantage", "нет данных",
                "modelWinShare", "нет данных", "meanBootstrap95", "нет данных",
                "fallbackReasons", List.of())))
            .contains("нет данных"));
        assertTrue(render("game_analysis", Map.ofEntries(
            Map.entry("game", Map.of("id", 1, "name", "Моя игра", "current_day", 45)),
            Map.entry("own", Map.of("balance", 2)), Map.entry("ranking", List.of()),
            Map.entry("result", List.of()), Map.entry("cycles", List.of()), Map.entry("ads", List.of()),
            Map.entry("days", List.of()), Map.entry("advice", List.of()),
            Map.entry("investmentPayments", List.of()), Map.entry("businessPayments", List.of()),
            Map.entry("taxes", List.of()))).contains("Моя игра"));
    }

    @Test
    void runTemplateEscapesPreDecisionJson() throws Exception {
        var series = new ExperimentSeries();
        series.setId(1L);
        var run = new ExperimentRun();
        run.setId(2L);
        run.setSeries(series);
        run.setNumber(1);
        run.setSeed(Long.MAX_VALUE);
        run.setMarket("BUDGET");
        String html = render("bot_analytics_run", Map.ofEntries(Map.entry("title", "Игра"),
            Map.entry("run", run), Map.entry("game", Map.of("name", "<script>alert(1)</script>")),
            Map.entry("players", List.of()), Map.entry("days", List.of()), Map.entry("cycles", List.of()),
            Map.entry("ads", List.of()), Map.entry("investmentPayments", List.of()),
            Map.entry("businessPayments", List.of()), Map.entry("taxes", List.of()),
            Map.entry("trades", List.of()), Map.entry("decisions", List.of()),
            Map.entry("episodes", List.of()), Map.entry("bot", 42L),
            Map.entry("backUrl", "/admin/bot-analytics/evaluations/1"),
            Map.entry("backLabel", "К парной оценке"),
            Map.entry("seriesUrl", "/admin/bot-analytics/series/1"),
            Map.entry("botSelectionUrl", "/admin/bot-analytics/runs/2?from=evaluation")));
        assertTrue(html.contains("9223372036854775807"));
        assertTrue(html.contains("&lt;script&gt;"));
        assertTrue(html.contains("href=\"/admin/bot-analytics/evaluations/1\""));
    }
}
