package ru.itis.diploma.service;

import org.junit.jupiter.api.Test;
import ru.itis.diploma.dto.StrategySnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class StrategyCoordinatorTest {
    private final StrategyCoordinator coordinator = new StrategyCoordinator(new StrategyMarketAgent(),
        new StrategyProductionAgent(), new StrategyFinancialAgent(), (s, c) -> java.util.Optional.empty());

    private StrategySnapshot snapshot(int days, BigDecimal balance, List<StrategySnapshot.Payment> payments) {
        return new StrategySnapshot(1, 12, true, balance, 10, new BigDecimal("2"),
            BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN,
            12, 10, 20, 2, new BigDecimal("0.5"), new BigDecimal("2"), 0, 2,
            IntStream.range(0, days).mapToObj(i -> new StrategySnapshot.Sale(i + 1, i + 4, 10, 10)).toList(),
            List.of(new StrategySnapshot.Competitor(new BigDecimal("3"), BigDecimal.ONE, 2, 0)), payments, "HOLD");
    }

    @Test
    void withSevenDaysUsesObservedPercentilesAndKnownPayments() {
        var advice = coordinator.recommend(snapshot(7, new BigDecimal("100"),
            List.of(new StrategySnapshot.Payment(13, new BigDecimal("20"), "Кредит"))), null);
        assertEquals(3, advice.getRecommendations().size());
        var hold = advice.getRecommendations().get(0);
        assertEquals(5, hold.getLowSales());
        assertEquals(7, hold.getNormalSales());
        assertEquals(9, hold.getHighSales());
        assertEquals(new BigDecimal("29.00"), hold.getReserve()); // 1.25*20 + 0.2*20
    }

    @Test
    void withoutHistoryNeverFabricatesNumericDemand() {
        var advice = coordinator.recommend(snapshot(2, new BigDecimal("100"), List.of()), null);
        assertFalse(advice.getRecommendations().isEmpty());
        assertNull(advice.getRecommendations().get(0).getLowSales());
    }

    @Test
    void unaffordableAlternativesAreExcludedWithoutChangingInput() {
        var snapshot = snapshot(7, BigDecimal.ZERO, List.of());
        var advice = coordinator.recommend(snapshot, null);
        assertTrue(advice.getRecommendations().isEmpty());
        assertEquals(BigDecimal.ZERO, snapshot.balance());
    }

    @Test
    void validatedModelEstimateRanksSafeActionsAndKeepsRuleFallback() {
        StrategyPredictor predictor = (state, cards) -> Optional.of(new StrategyPredictor.Predictions(
            "CatBoost", "v1", java.util.Arrays.asList(new StrategyPredictor.Estimate(2, 5, 8),
                new StrategyPredictor.Estimate(10, 12, 15), null)));
        var modelCoordinator = new StrategyCoordinator(new StrategyMarketAgent(),
            new StrategyProductionAgent(), new StrategyFinancialAgent(), predictor);
        var advice = modelCoordinator.recommend(snapshot(7, new BigDecimal("100"), List.of()), null);
        assertEquals(3, advice.getRecommendations().size());
        assertEquals("MODEL", advice.getRecommendations().get(0).getSource());
        assertEquals("RULES", advice.getRecommendations().get(2).getSource());
        assertEquals("v1", advice.getRecommendations().get(0).getModelVersion());
        assertNotNull(advice.getRecommendations().get(0).getLowCash());
    }

    @Test
    void outOfTrainingRangeKeepsRuleCardsAndExplainsAbstention() {
        StrategyPredictor predictor = (state, cards) -> Optional.of(new StrategyPredictor.Predictions(
            "CatBoost", "v2", java.util.Arrays.asList(null, null, null), List.of(),
            List.of("OUTSIDE_TRAIN_RANGE", "OUTSIDE_TRAIN_RANGE", "OUTSIDE_TRAIN_RANGE")));
        var advice = new StrategyCoordinator(new StrategyMarketAgent(), new StrategyProductionAgent(),
            new StrategyFinancialAgent(), predictor).recommend(snapshot(7, new BigDecimal("100"), List.of()), null);
        assertEquals("OUTSIDE_TRAIN_RANGE", advice.getModelFallbackReason());
        assertTrue(advice.getRecommendations().stream().allMatch(c -> "RULES".equals(c.getSource())));
    }

    @Test
    void competingModelsCanRecommendDifferentAlreadyValidatedCards() {
        StrategyPredictor predictor = (state, cards) -> Optional.of(new StrategyPredictor.Predictions(
            "CatBoost", "v1", List.of(new StrategyPredictor.Estimate(2, 5, 8),
                new StrategyPredictor.Estimate(10, 12, 15), new StrategyPredictor.Estimate(1, 2, 3)),
            List.of(new StrategyPredictor.Comparison("RandomForest", "v1", List.of(
                new StrategyPredictor.Estimate(20, 21, 22),
                new StrategyPredictor.Estimate(1, 2, 3), new StrategyPredictor.Estimate(1, 2, 3))))));
        var modelCoordinator = new StrategyCoordinator(new StrategyMarketAgent(),
            new StrategyProductionAgent(), new StrategyFinancialAgent(), predictor);
        var advice = modelCoordinator.recommend(snapshot(7, new BigDecimal("100"), List.of()), null);
        assertEquals(2, advice.getModelOpinions().size());
        assertNotEquals(advice.getModelOpinions().get(0).getAction(), advice.getModelOpinions().get(1).getAction());
        assertTrue(advice.getRecommendations().stream().anyMatch(c -> c.getName().equals(advice.getModelOpinions().get(1).getAction())));
    }
}
