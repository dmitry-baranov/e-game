package ru.itis.diploma.experiment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import ru.itis.diploma.experiment.analytics.BotAnalyticsController;
import ru.itis.diploma.experiment.analytics.ExperimentReportController;
import ru.itis.diploma.game.GameViewExtension;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"experiment.enabled=false", "spring.jpa.show-sql=false"})
class OrdinaryGameIsolationIntegrationTest {
    @Autowired private ObjectProvider<GameViewExtension> gameView;
    @Autowired private ApplicationContext context;

    @Test
    void regularGameHasNoExperimentalLinkOrReportingControllers() {
        assertNull(gameView.getIfAvailable());
        assertTrue(context.getBeansOfType(BotAnalyticsController.class).isEmpty());
        assertTrue(context.getBeansOfType(ExperimentReportController.class).isEmpty());
    }
}
