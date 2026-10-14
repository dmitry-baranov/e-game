package ru.itis.diploma.game.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.itis.diploma.game.model.*;
import ru.itis.diploma.game.repository.*;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CycleObservationServiceTest {
    private final ProductionParametersRepository productions = mock(ProductionParametersRepository.class);
    private final StatisticsInfoRepository statistics = mock(StatisticsInfoRepository.class);
    private final AdvertisementRepository advertisements = mock(AdvertisementRepository.class);
    private final CycleObservationRepository observations = mock(CycleObservationRepository.class);
    private final CycleObservationService service = new CycleObservationService(
        productions, statistics, advertisements, observations);

    @Test
    void marksCycleCompleteOnlyWhenEveryExpectedUniqueDayExists() {
        var manufacturer = Manufacturer.builder().id(7L).build();
        var cycle = ProductionParameters.builder().id(11L).manufacturer(manufacturer).startDate(3)
            .timeToMarket(2).productCount(10).price(BigDecimal.TEN).build();
        when(productions.findByManufacturer_Game_IdOrderByStartDateAscIdAsc(1L)).thenReturn(List.of(cycle));
        when(advertisements.findByManufacturer_Game_IdOrderByStartDateAscIdAsc(1L)).thenReturn(List.of());
        when(observations.findByManufacturer_Game_Id(1L)).thenReturn(List.of());
        when(statistics.findByManufacturer_Game_IdOrderByTradeDateAscIdAsc(1L)).thenReturn(List.of(
            day(manufacturer, 4, 2), day(manufacturer, 5, 3)));

        service.refresh(1L);

        var saved = captureSaved();
        assertTrue(saved.isComplete());
        assertEquals(2, saved.getObservedDays());
        assertEquals(5, saved.getProductsSold());
        assertEquals(new BigDecimal("50"), saved.getRevenue());
        assertEquals(5, cycle.getEndDate());
    }

    @Test
    void doesNotTreatARepeatedOrMissingDayAsComplete() {
        var manufacturer = Manufacturer.builder().id(7L).build();
        var cycle = ProductionParameters.builder().id(11L).manufacturer(manufacturer).startDate(3)
            .endDate(5).timeToMarket(2).productCount(10).price(BigDecimal.TEN).build();
        when(productions.findByManufacturer_Game_IdOrderByStartDateAscIdAsc(1L)).thenReturn(List.of(cycle));
        when(advertisements.findByManufacturer_Game_IdOrderByStartDateAscIdAsc(1L)).thenReturn(List.of());
        when(observations.findByManufacturer_Game_Id(1L)).thenReturn(List.of());
        when(statistics.findByManufacturer_Game_IdOrderByTradeDateAscIdAsc(1L)).thenReturn(List.of(
            day(manufacturer, 4, 2), day(manufacturer, 4, 9)));

        service.refresh(1L);

        assertFalse(captureSaved().isComplete());
    }

    @Test
    void recomputesExistingObservationInsteadOfAddingToPreviousTotals() {
        var manufacturer = Manufacturer.builder().id(7L).build();
        var cycle = ProductionParameters.builder().id(11L).manufacturer(manufacturer).startDate(3)
            .endDate(5).timeToMarket(2).productCount(10).price(BigDecimal.TEN).build();
        var existing = new CycleObservation();
        existing.setId(19L);
        existing.setProductionParameters(cycle);
        existing.setProductsSold(100);
        when(productions.findByManufacturer_Game_IdOrderByStartDateAscIdAsc(1L)).thenReturn(List.of(cycle));
        when(advertisements.findByManufacturer_Game_IdOrderByStartDateAscIdAsc(1L)).thenReturn(List.of());
        when(observations.findByManufacturer_Game_Id(1L)).thenReturn(List.of(existing));
        when(statistics.findByManufacturer_Game_IdOrderByTradeDateAscIdAsc(1L)).thenReturn(List.of(
            day(manufacturer, 4, 2), day(manufacturer, 5, 3)));

        service.refresh(1L);

        var saved = captureSaved();
        assertSame(existing, saved);
        assertEquals(5, saved.getProductsSold());
        assertEquals(new BigDecimal("50"), saved.getRevenue());
    }

    @SuppressWarnings("unchecked")
    private CycleObservation captureSaved() {
        ArgumentCaptor<Iterable<CycleObservation>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(observations).saveAll(captor.capture());
        return captor.getValue().iterator().next();
    }

    private StatisticsInfo day(Manufacturer manufacturer, int day, int sold) {
        return StatisticsInfo.builder().manufacturer(manufacturer).tradeDate(day).productsSold(sold)
            .productsProduced(1).productsInStock(0).paidTaxesAmount(BigDecimal.ZERO)
            .repaidInvestmentCreditAmount(BigDecimal.ZERO).repaidBusinessCreditAmount(BigDecimal.ZERO).build();
    }
}
