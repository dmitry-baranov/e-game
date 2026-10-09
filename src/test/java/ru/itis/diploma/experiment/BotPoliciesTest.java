package ru.itis.diploma.experiment;

import org.junit.jupiter.api.Test;
import ru.itis.diploma.dto.StrategyAdvice;
import ru.itis.diploma.dto.StrategySnapshot;
import ru.itis.diploma.service.StrategyCoordinator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BotPoliciesTest {
    @Test
    void allThirtyTwoNewPoliciesProduceReproducibleValidActionsWithOnlyOwnHistory() {
        assertEquals(38, BotPolicies.NAMES.length);
        assertEquals(38, new HashSet<>(Arrays.asList(BotPolicies.NAMES)).size());
        var config = new ExperimentConfig();
        config.validate();
        assertEquals(38, config.policies().length);
        var policies = new BotPolicies(mock(StrategyCoordinator.class));
        var start = new StrategySnapshot(1, 0, false, null, 0, BigDecimal.TEN,
            BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("5"), BigDecimal.ONE, BigDecimal.ONE,
            12, null, null, null, null, null, 0, 0, List.of(), List.of(), List.of(), null);
        var view = new StrategySnapshot(1, 20, true, new BigDecimal("1200"), 3, BigDecimal.TEN,
            BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("5"), BigDecimal.ONE, BigDecimal.ONE,
            12, 10, 30, 2, new BigDecimal("0.6"), new BigDecimal("11"), 0, 3,
            List.of(new StrategySnapshot.Sale(20, 7, 10, 3), new StrategySnapshot.Sale(19, 8, 10, 0),
                new StrategySnapshot.Sale(18, 6, 10, 0)),
            List.of(new StrategySnapshot.Competitor(new BigDecimal("12"), new BigDecimal("0.8"), 3, 3)),
            List.of(new StrategySnapshot.Payment(23, new BigDecimal("20"), "Кредит")), null);
        var history = List.of(new ObservedCycle(10, 3, 30, new BigDecimal("10"), new BigDecimal("0.6"), 2,
            0, 0, 12, new BigDecimal("120")), new ObservedCycle(14, 3, 30, new BigDecimal("11"),
            new BigDecimal("0.6"), 2, 2, 1, 15, new BigDecimal("165")));
        for (int i = 6; i < BotPolicies.NAMES.length; i++) {
            String name = BotPolicies.NAMES[i];
            var initial = policies.decide(start, name, 42, 0, null, List.of()).action();
            assertTrue(initial.getProductCount() > 0 && initial.getAdvertisingDays() <=
                (initial.getProductCount() + 9) / 10, name + " starting plan");
            var first = policies.decide(view, name, 42, 0, null, history).action();
            var again = policies.decide(view, name, 42, 0, null, history).action();
            assertEquals(first, again, name);
            assertTrue(first.getProductCount() >= 1 && first.getProductCount() <= 60, name);
            assertTrue(first.getAssortment() >= 1 && first.getAssortment() <= first.getProductCount(), name);
            assertTrue(first.getQualityIndex().signum() >= 0 && first.getQualityIndex().compareTo(BigDecimal.ONE) <= 0, name);
            assertTrue(first.getPrice().compareTo(BigDecimal.ZERO) > 0, name);
            assertTrue(first.getAdvertisingIntensityIndex() >= 0 && first.getAdvertisingIntensityIndex() <= 7, name);
            assertEquals(first.getAdvertisingIntensityIndex() == 0, first.getAdvertisingDays() == 0, name);
            assertTrue(first.getAdvertisingDays() <= (first.getProductCount() + 9) / 10, name);
        }
        assertThrows(IllegalArgumentException.class, () -> policies.decide(view, "INVALID", 1, 0, null, history));
        assertTrue(policies.decide(view, "PRICE_MEDIAN", 1, 0, null, history).action().getPrice()
            .compareTo(policies.decide(view, "PRICE_PREMIUM", 1, 0, null, history).action().getPrice()) < 0);
        assertTrue(policies.decide(view, "SHORT_CYCLE", 1, 0, null, history).action().getProductCount()
            < policies.decide(view, "LONG_CYCLE", 1, 0, null, history).action().getProductCount());
    }

    @Test
    void inheritedAdvertisementCannotProduceInvalidZeroDayAction() {
        var coordinator = mock(StrategyCoordinator.class);
        var card = new StrategyAdvice.Card();
        card.setKey("HOLD"); card.setProductCount(30); card.setCapacity(10);
        card.setPrice(BigDecimal.TEN); card.setQuality(new BigDecimal("0.8"));
        card.setAssortment(3); card.setAdvertisingIntensity(1); card.setAdvertisingDays(0);
        var advice = new StrategyAdvice(); advice.setRecommendations(List.of(card));
        when(coordinator.recommend(any(), isNull())).thenReturn(advice);
        var view = new StrategySnapshot(1, 4, true, BigDecimal.TEN, 0, BigDecimal.TEN,
            BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE,
            12, 10, 30, 3, new BigDecimal("0.8"), BigDecimal.TEN, 1, 3,
            List.of(), List.of(), List.of(), null);
        var decision = new BotPolicies(coordinator).decide(view, "EVAL_RULES", 42, 0, null);
        assertEquals(1, decision.action().getAdvertisingIntensityIndex());
        assertEquals(1, decision.action().getAdvertisingDays());
        assertEquals(List.of(card), decision.candidates());
    }
}
