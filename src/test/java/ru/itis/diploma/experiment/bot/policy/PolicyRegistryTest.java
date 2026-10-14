package ru.itis.diploma.experiment.bot.policy;

import org.junit.jupiter.api.Test;
import ru.itis.diploma.game.dto.StrategyAdvice;
import ru.itis.diploma.game.dto.StrategySnapshot;
import ru.itis.diploma.game.service.StrategyCoordinator;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PolicyRegistryTest {
    @Test
    void registryContainsEveryCatalogEntryExactlyOnce() {
        String[] names = PolicyRegistry.selectableNames();

        assertEquals(38, names.length);
        assertEquals(names.length, new HashSet<>(List.of(names)).size());
        assertEquals(new HashSet<>(List.of(names)), PolicyRegistry.selectableDescriptions().keySet());
    }

    @Test
    void unknownPolicyFailsAtTheRegistryBoundary() {
        var snapshot = snapshot();
        var registry = new PolicyRegistry(mock(StrategyCoordinator.class));

        var error = assertThrows(IllegalArgumentException.class,
            () -> registry.decide("UNKNOWN", new PolicyContext(snapshot, 1, 0, null, List.of())));

        assertEquals("Неизвестная политика бота: UNKNOWN", error.getMessage());
    }

    @Test
    void everyMetadataEntryHasARegisteredHandler() {
        var coordinator = mock(StrategyCoordinator.class);
        var advice = new StrategyAdvice();
        advice.setRecommendations(List.of());
        when(coordinator.recommend(any(), isNull())).thenReturn(advice);
        var registry = new PolicyRegistry(coordinator);

        for (PolicyCatalog policy : PolicyCatalog.values()) {
            var decision = registry.decide(policy.name(),
                new PolicyContext(snapshot(), 42, 0, null, List.of()));
            assertNotNull(decision.action(), policy.name());
        }
    }

    @Test
    void recommendationAdapterKeepsTheThreeModeContract() {
        var coordinator = mock(StrategyCoordinator.class);
        var advice = new StrategyAdvice();
        advice.setRecommendations(List.of(card("CAUTIOUS", 20), card("QUALITY", 30)));
        when(coordinator.recommend(any(), isNull())).thenReturn(advice);
        var registry = new PolicyRegistry(coordinator);
        var context = new PolicyContext(snapshot(), 42, 0, null, List.of());

        var rules = registry.decide("RULES", context);
        var evaluationRules = registry.decide("EVAL_RULES", context);
        var model = registry.decide("MODEL", context);

        assertEquals(20, rules.action().getProductCount());
        assertEquals(30, evaluationRules.action().getProductCount());
        assertEquals(30, model.action().getProductCount());
        assertFalse(rules.fallback());
        assertFalse(evaluationRules.fallback());
        assertTrue(model.fallback());
        assertEquals("STARTING_GAME", model.fallbackReason());
    }

    private static StrategyAdvice.Card card(String key, int count) {
        var card = new StrategyAdvice.Card();
        card.setKey(key);
        card.setProductCount(count);
        card.setPrice(BigDecimal.TEN);
        card.setQuality(new BigDecimal("0.6"));
        card.setAssortment(2);
        card.setAdvertisingIntensity(0);
        card.setAdvertisingDays(0);
        return card;
    }

    private static StrategySnapshot snapshot() {
        return new StrategySnapshot(1, 0, false, null, 0, BigDecimal.TEN,
            BigDecimal.ONE, BigDecimal.TEN, new BigDecimal("5"), BigDecimal.ONE, BigDecimal.ONE,
            12, null, null, null, null, null, 0, 0, List.of(), List.of(), List.of(), null);
    }
}
