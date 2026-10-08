package ru.itis.diploma.experiment;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExperimentScenariosTest {
    private final ExperimentScenarios generator = new ExperimentScenarios();

    @Test
    void scenarioDoesNotDependOnWorkerOrder() {
        var c = new ExperimentConfig();
        c.setMinBots(10); c.setMaxBots(30); c.setMaxDays(180); c.setTarget(1000); c.setMaxAttempts(1500);
        var first = generator.generate(c, 42);
        generator.generate(c, 18);
        var again = generator.generate(c, 42);
        assertEquals(first.seed(), again.seed());
        assertEquals(first.market(), again.market());
        assertEquals(first.bots(), again.bots());
        assertEquals(first.horizon(), again.horizon());
        assertEquals(first.game().getDailySpendingLimit(), again.game().getDailySpendingLimit());
        assertTrue(first.horizon() >= c.getMinDays());
        assertTrue(java.util.stream.IntStream.rangeClosed(1, 30)
            .map(i -> generator.generate(c, i).horizon()).distinct().count() > 1,
            "Сроки партий должны различаться в зависимости от рынка");
    }

    @Test
    void evaluationUsesSameWorldForBothPolicies() {
        var c = new ExperimentConfig();
        c.setMode("EVALUATE");
        var rules = generator.generate(c, 1);
        var model = generator.generate(c, 2);
        assertEquals(rules.seed(), model.seed());
        assertEquals(rules.horizon(), model.horizon());
        assertEquals(rules.game().getPurchaseLimit(), model.game().getPurchaseLimit());
        c.setTarget(3);
        assertThrows(IllegalArgumentException.class, c::validate);
    }

    @Test
    void selectedMarketsAndPoliciesAreValidated() {
        var c = new ExperimentConfig();
        c.setMarketMix("BUDGET, ADVERTISING");
        c.setPolicyMix("CAUTIOUS, PRICE");
        c.validate();
        assertEquals(2, c.policies().length);
        assertTrue(java.util.stream.IntStream.rangeClosed(1, 15)
            .mapToObj(i -> generator.generate(c, i).market())
            .allMatch(m -> m.equals("Бюджетный") || m.equals("Дорогая реклама")));
        c.setPolicyMix("PRICE, HIDDEN");
        assertThrows(IllegalArgumentException.class, c::validate);
    }
}
