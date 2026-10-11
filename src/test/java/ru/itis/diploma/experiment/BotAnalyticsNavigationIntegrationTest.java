package ru.itis.diploma.experiment;

import ru.itis.diploma.experiment.model.ExperimentRun;
import ru.itis.diploma.experiment.repository.ExperimentRunRepository;
import ru.itis.diploma.experiment.service.ExperimentManager;
import ru.itis.diploma.experiment.service.ExperimentScenarios;
import ru.itis.diploma.experiment.controller.ExperimentController;
import ru.itis.diploma.experiment.config.ExperimentConfig;

import ru.itis.diploma.experiment.analytics.BotAnalyticsController;
import freemarker.core.HTMLOutputFormat;
import freemarker.template.Configuration;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import ru.itis.diploma.game.GameViewExtension;

import java.time.Duration;
import java.time.Instant;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"experiment.enabled=true", "spring.jpa.show-sql=false"})
class BotAnalyticsNavigationIntegrationTest {
    @Autowired private ExperimentManager manager;
    @Autowired private ExperimentRunRepository runs;
    @Autowired private BotAnalyticsController analytics;
    @Autowired private ExperimentController experiments;
    @Autowired private GameViewExtension gameView;

    @BeforeEach
    void admin() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "test-admin", "", List.of(new SimpleGrantedAuthority("ADMIN"))));
    }

    @AfterEach
    void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test
    void ordinaryUserCannotReadExperimentalReports() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "test-user", "", List.of(new SimpleGrantedAuthority("USER"))));
        assertThrows(AccessDeniedException.class, () -> analytics.index("", "", 0, new ExtendedModelMap()));
        assertThrows(AccessDeniedException.class, () -> experiments.status(1L));
    }

    @Test
    void filteredMarketAndBotPageReadRealFinishedGame() throws InterruptedException {
        var config = new ExperimentConfig();
        config.setMarketMix("QUALITY_FOCUS");
        config.setTarget(1);
        config.setMaxAttempts(1);
        config.setMinDays(30);
        config.setMaxDays(30);
        config.setMinBots(2);
        config.setMaxBots(2);
        config.setPolicyMix("CAUTIOUS,PRICE");
        long id = manager.create(config).getId();
        ExperimentRun run = null;
        Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
        while (Instant.now().isBefore(deadline)) {
            var attempts = runs.findBySeriesIdOrderByNumberDesc(id);
            if (!attempts.isEmpty() && List.of("COMPLETED", "INCOMPLETE", "ERROR").contains(attempts.get(0).getStatus())) {
                run = attempts.get(0);
                break;
            }
            Thread.sleep(100);
        }
        assertNotNull(run, "Игра должна завершиться на изолированной БД");
        long runId = run.getId();
        assertEquals(java.util.Optional.of(runId), gameView.runForGame(run.getGame().getId()));
        String market = run.getMarket();
        assertNotEquals("ERROR", run.getStatus(), run.getError());
        assertEquals("Ставка на качество", run.getMarket());
        assertEquals(1, experiments.status(id).runs().size());

        var model = new ExtendedModelMap();
        assertEquals("bot_analytics_series", analytics.series(id, 0, "", run.getMarket(), model));
        assertEquals(1L, ((Number) model.get("count")).longValue());
        assertEquals(1, ((List<?>) model.get("games")).size());
        assertTrue(((List<?>) model.get("markets")).contains(run.getMarket()));
        for (String known : ExperimentScenarios.MARKET_NAMES)
            assertEquals("bot_analytics_series", analytics.series(id, 0, "", known, new ExtendedModelMap()));
        assertThrows(ResponseStatusException.class,
            () -> analytics.series(id, 0, "", "несуществующий рынок", new ExtendedModelMap()));
        var otherMarket = new ExtendedModelMap();
        analytics.series(id, 0, "", "Бюджетный", otherMarket);
        assertEquals(0L, ((Number) otherMarket.get("count")).longValue());

        var details = new ExtendedModelMap();
        assertEquals("bot_analytics_run", analytics.run(run.getId(), null, null, "series", 0, "",
            run.getMarket(), details));
        assertEquals(run.getMarket(), ((ExperimentRun) details.get("run")).getMarket());
        assertEquals(2, ((List<?>) details.get("players")).size());
        assertFalse(((List<?>) details.get("days")).isEmpty());
        assertFalse(((List<?>) details.get("cycles")).isEmpty());
        assertFalse(((List<?>) details.get("decisions")).isEmpty());
        assertTrue(((String) details.get("backUrl")).contains("market="));
        assertTrue(((List<Map<String, Object>>) details.get("decisions")).stream().allMatch(d -> d.containsKey("plan")));
        long firstBot = ((Number) ((List<Map<String, Object>>) details.get("players")).get(0).get("id")).longValue();
        long secondBot = ((Number) ((List<Map<String, Object>>) details.get("players")).get(1).get("id")).longValue();
        var compared = new ExtendedModelMap();
        analytics.run(run.getId(), firstBot, secondBot, "series", 0, "", run.getMarket(), compared);
        assertFalse(((List<?>) compared.get("compareDays")).isEmpty());
        assertThrows(ResponseStatusException.class, () -> analytics.run(runId, firstBot, firstBot,
            "series", 0, "", market, new ExtendedModelMap()));
        assertTrue(render("bot_analytics_series", model).contains("Ставка на качество"));
        String html = render("bot_analytics_run", details);
        assertTrue(html.contains("Хронология решений до торговли"));
        assertTrue(html.contains("data-cycle="));
        assertTrue(html.contains("Предложенный план"));
        var index = new ExtendedModelMap();
        analytics.index("", "", 0, index);
        assertTrue(render("bot_analytics", index).contains("Результаты"));
    }

    private String render(String name, Map<String, Object> model) {
        try {
            var templates = new Configuration(Configuration.VERSION_2_3_31);
            templates.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
            templates.setOutputFormat(HTMLOutputFormat.INSTANCE);
            templates.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
            var result = new StringWriter();
            templates.getTemplate(name + ".ftlh").process(model, result);
            return result.toString();
        } catch (Exception e) {
            throw new AssertionError("Не удалось отобразить " + name, e);
        }
    }
}
