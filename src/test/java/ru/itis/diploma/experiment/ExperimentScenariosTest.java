package ru.itis.diploma.experiment;

import ru.itis.diploma.experiment.service.ExperimentScenarios;
import ru.itis.diploma.experiment.config.ExperimentConfig;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
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

    @Test
    void existingMarketsRetainMarketV3ParametersAndOldVersionRejectsNewTypes() {
        for (String market : List.of("BUDGET", "COMPETITIVE", "ADVERTISING")) {
            var c = new ExperimentConfig();
            c.setMarketMix(market);
            for (int number = 1; number <= 10; number++) {
                var old = generator.generate(c, number, "market-v3");
                var updated = generator.generate(c, number, "market-v4");
                assertEquals(old.seed(), updated.seed());
                assertEquals(old.market(), updated.market());
                assertEquals(old.bots(), updated.bots());
                assertEquals(old.horizon(), updated.horizon());
                assertEquals(old.game(), updated.game());
            }
        }
        var c = new ExperimentConfig();
        c.setMarketMix("PERISHABLE");
        assertThrows(IllegalArgumentException.class, () -> generator.generate(c, 1, "market-v3"));
    }

    @Test
    void newMarketsChangeOnlyTheirDeclaredConditions() {
        var c = new ExperimentConfig();
        assertEquals(8, c.markets().length);
        c.setMarketMix("COMPETITIVE");
        var baseline = generator.generate(c, 1);
        var standard = baseline.game();
        for (String market : ExperimentScenarios.MARKETS.subList(3, 8)) {
            c.setMarketMix(market);
            var scenario = generator.generate(c, 1);
            var game = scenario.game();
            assertEquals(baseline.seed(), scenario.seed(), market);
            assertEquals(baseline.bots(), scenario.bots(), market);
            assertEquals(standard.getBaseCostPrice(), game.getBaseCostPrice(), market);
            assertEquals(standard.getDailySpendingLimit(), game.getDailySpendingLimit(), market);
            switch (market) {
                case "QUALITY_FOCUS" -> {
                    assertEquals(new BigDecimal("6"), game.getQualityWeight());
                    assertEquals(standard.getHabitWeight(), game.getHabitWeight());
                }
                case "LOYALTY" -> {
                    assertEquals(new BigDecimal("10"), game.getHabitWeight());
                    assertEquals(20, game.getHabitTrackingDays());
                }
                case "CREDIT_CRUNCH" -> {
                    assertEquals(new BigDecimal("18"), game.getInterestRateInvestmentCredit());
                    assertEquals(new BigDecimal("24"), game.getInterestRateBusinessCredit());
                    assertEquals(new BigDecimal("60"), game.getProductPower());
                }
                case "PERISHABLE" -> {
                    assertTrue(game.getAbsoluteQualityProductLife() >= 4 && game.getAbsoluteQualityProductLife() <= 7);
                    assertEquals(Math.min(c.getMaxDays(), Math.max(c.getMinDays(),
                        Math.max(45, game.getAbsoluteQualityProductLife() * 7 + 10))), scenario.horizon());
                }
                case "OVERSUPPLY" -> {
                    assertEquals(Math.max(1, standard.getPurchaseLimit() / 3), game.getPurchaseLimit());
                    assertEquals(standard.getAbsoluteQualityProductLife(), game.getAbsoluteQualityProductLife());
                }
                default -> fail("Неизвестный рынок: " + market);
            }
        }
        c.setMarketMix("QUALITY_FOCUS,UNKNOWN");
        assertThrows(IllegalArgumentException.class, c::validate);
    }

    @Test
    void evaluationPairsUseIdenticalWorldsForEveryNewMarket() {
        var c = new ExperimentConfig();
        c.setMode("EVALUATE");
        for (String market : ExperimentScenarios.MARKETS.subList(3, 8)) {
            c.setMarketMix(market);
            var rules = generator.generate(c, 1);
            var model = generator.generate(c, 2);
            assertEquals(rules.seed(), model.seed());
            assertEquals(rules.market(), model.market());
            assertEquals(rules.horizon(), model.horizon());
            // Only the display name differs: the two games share every buyer/financing parameter.
            var displayName = model.game().getName();
            model.game().setName(rules.game().getName());
            assertEquals(rules.game(), model.game());
            model.game().setName(displayName);
        }
    }
}
