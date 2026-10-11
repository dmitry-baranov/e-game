package ru.itis.diploma.experiment.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.itis.diploma.game.dto.StrategySnapshot;
import ru.itis.diploma.game.model.CycleObservation;
import ru.itis.diploma.game.model.Manufacturer;
import ru.itis.diploma.game.model.ProductionParameters;
import ru.itis.diploma.game.repository.CycleObservationRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BotDecisionsObservationTest {
    @Test
    @SuppressWarnings("unchecked")
    void versionSixReadsMaterializedCompleteCycles() {
        var policies = mock(BotPolicies.class);
        var observations = mock(CycleObservationRepository.class);
        var service = new BotDecisions(policies, observations, new ObjectMapper());
        var player = Manufacturer.builder().id(7L).selectedStrategy("PRICE_REVENUE_WINDOW").build();
        var production = ProductionParameters.builder().productCount(20).price(BigDecimal.TEN)
            .qualityIndex(BigDecimal.ONE).assortment(2).build();
        var observation = new CycleObservation();
        observation.setProductionParameters(production);
        observation.setStartDate(3);
        observation.setExpectedDays(2);
        observation.setAdvertisingIntensity(4);
        observation.setAdvertisingDays(2);
        observation.setProductsSold(9);
        observation.setRevenue(new BigDecimal("90"));
        when(observations.findByManufacturerIdAndCompleteIsTrueAndEndDateLessThanEqualOrderByStartDateAsc(7L, 8))
            .thenReturn(List.of(observation));
        var expected = mock(BotPolicies.Decision.class);
        when(policies.decide(any(), eq("PRICE_REVENUE_WINDOW"), anyLong(), eq(0), isNull(), anyList()))
            .thenReturn(expected);
        var snapshot = mock(StrategySnapshot.class);
        when(snapshot.day()).thenReturn(8);

        assertEquals(expected, service.decide(13L, "bots-v6", null, player, snapshot, 0));

        var history = ArgumentCaptor.forClass(List.class);
        verify(policies).decide(eq(snapshot), eq("PRICE_REVENUE_WINDOW"), anyLong(), eq(0), isNull(), history.capture());
        var cycle = (ObservedCycle) history.getValue().get(0);
        assertEquals(9, cycle.sold());
        assertEquals(new BigDecimal("90"), cycle.revenue());
    }
}
