package ru.itis.diploma.experiment;

import org.junit.jupiter.api.Test;
import ru.itis.diploma.dto.StrategyAdvice;
import ru.itis.diploma.dto.StrategySnapshot;
import ru.itis.diploma.service.StrategyCoordinator;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BotPoliciesTest {
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
    }
}
