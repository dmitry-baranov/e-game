package ru.itis.diploma.experiment.bot;

import org.junit.jupiter.api.Test;
import ru.itis.diploma.game.dto.StrategySnapshot;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotPoliciesTest {
    @Test
    void onlyThreeUserPoliciesAreSelectable() {
        assertEquals(List.of("CAUTIOUS", "PRICE", "STOCK"), List.of(BotPolicies.NAMES));
        assertEquals(new HashSet<>(List.of(BotPolicies.NAMES)), BotPolicies.DESCRIPTIONS.keySet());
        assertTrue(!BotPolicies.DESCRIPTIONS.containsKey("RULES"));
        assertTrue(!BotPolicies.DESCRIPTIONS.containsKey("EVAL_RULES"));
        assertTrue(!BotPolicies.DESCRIPTIONS.containsKey("MODEL"));
    }

    @Test
    void selectablePoliciesProduceSimpleStartingPlans() {
        var policies = new BotPolicies();
        var start = new StrategySnapshot(1, 0, false, null, 0, BigDecimal.TEN,
            BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("5"), BigDecimal.ONE, BigDecimal.ONE,
            12, null, null, null, null, null, 0, 0, List.of(), List.of(), List.of(), null);

        assertEquals(20, policies.decide(start, "CAUTIOUS", 0).action().getProductCount());
        assertEquals(30, policies.decide(start, "PRICE", 0).action().getProductCount());
        assertEquals(40, policies.decide(start, "STOCK", 0).action().getProductCount());
        assertThrows(IllegalArgumentException.class,
            () -> policies.decide(start, "PRICE_MEDIAN", 0));
    }

    @Test
    void priceUsesVisibleCompetitorWithoutSellingBelowMinimumMarkup() {
        var policies = new BotPolicies();
        var competitor = new StrategySnapshot.Competitor(
            new BigDecimal("5"), new BigDecimal("0.5"), 2, 0);
        var view = new StrategySnapshot(1, 4, true, BigDecimal.TEN, 0, BigDecimal.TEN,
            BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("5"), BigDecimal.ONE, BigDecimal.ONE,
            12, 10, 30, 2, new BigDecimal("0.6"), BigDecimal.TEN, 0, 3,
            List.of(), List.of(competitor), List.of(), null);

        var plan = policies.decide(view, "PRICE", 0).action();
        assertEquals(new BigDecimal("6.30"), plan.getPrice());
        assertEquals(30, plan.getProductCount());
    }

    @Test
    void stockPolicyReducesBatchWhenStockExceedsPreviousProduction() {
        var policies = new BotPolicies();
        var view = new StrategySnapshot(1, 4, true, BigDecimal.TEN, 40, BigDecimal.TEN,
            BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("5"), BigDecimal.ONE, BigDecimal.ONE,
            12, 10, 30, 2, new BigDecimal("0.6"), BigDecimal.TEN, 0, 3,
            List.of(), List.of(), List.of(), null);

        var plan = policies.decide(view, "STOCK", 0).action();
        assertEquals(20, plan.getProductCount());
    }

    @Test
    void recommendationPoliciesCannotBeSelected() {
        var policies = new BotPolicies();
        var view = new StrategySnapshot(1, 0, false, null, 0, BigDecimal.TEN,
            BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("5"), BigDecimal.ONE, BigDecimal.ONE,
            12, null, null, null, null, null, 0, 0, List.of(), List.of(), List.of(), null);
        for (String name : List.of("RULES", "EVAL_RULES", "MODEL"))
            assertThrows(IllegalArgumentException.class, () -> policies.decide(view, name, 0));
    }
}
