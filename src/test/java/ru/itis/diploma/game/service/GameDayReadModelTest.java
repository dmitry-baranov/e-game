package ru.itis.diploma.game.service;

import org.junit.jupiter.api.Test;
import ru.itis.diploma.game.model.*;
import ru.itis.diploma.game.repository.*;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameDayReadModelTest {
    @Test
    void buildsAllPreDecisionSnapshotsWithOneBatchCallPerDataSet() {
        var manufacturers = mock(ManufacturerRepository.class);
        var productions = mock(ProductionParametersRepository.class);
        var advertisements = mock(AdvertisementRepository.class);
        var statistics = mock(StatisticsInfoRepository.class);
        var investments = mock(InvestmentCreditPaymentRepository.class);
        var business = mock(BusinessCreditPaymentRepository.class);
        var service = new GameDayReadModel(manufacturers, productions, advertisements, statistics, investments, business);
        var game = Game.builder().id(1L).currentDay(4).baseCostPrice(BigDecimal.ONE)
            .baseAdvertisementPrice(BigDecimal.ONE).productPower(BigDecimal.ONE).salesTax(BigDecimal.ONE)
            .interestRateInvestmentCredit(BigDecimal.ONE).interestRateBusinessCredit(BigDecimal.ONE)
            .investmentCreditTermMonths(BigDecimal.TEN).build();
        var first = player(10L, 100L, game);
        var second = player(20L, 200L, game);
        var firstCycle = cycle(101L, first, new BigDecimal("7"));
        var secondCycle = cycle(201L, second, new BigDecimal("8"));
        when(manufacturers.findGamePlayers(1L)).thenReturn(List.of(first, second));
        when(productions.findByManufacturer_Game_IdOrderByStartDateAscIdAsc(1L))
            .thenReturn(List.of(firstCycle, secondCycle));
        when(advertisements.findByManufacturer_Game_IdOrderByStartDateAscIdAsc(1L)).thenReturn(List.of());
        when(statistics.findByManufacturer_Game_IdOrderByTradeDateAscIdAsc(1L)).thenReturn(List.of());
        when(investments.findAllByManufacturer_Game_Id(1L)).thenReturn(List.of());
        when(business.findAllByProductionParametersIdInAndNextDateAfter(List.of(101L, 201L), 4)).thenReturn(List.of());

        var snapshots = service.snapshotsBeforeDecisions(1L);

        assertEquals(2, snapshots.size());
        assertEquals(new BigDecimal("8"), snapshots.get(100L).competitors().get(0).price());
        assertEquals(new BigDecimal("7"), snapshots.get(200L).competitors().get(0).price());
        verify(manufacturers, times(1)).findGamePlayers(1L);
        verify(productions, times(1)).findByManufacturer_Game_IdOrderByStartDateAscIdAsc(1L);
        verify(advertisements, times(1)).findByManufacturer_Game_IdOrderByStartDateAscIdAsc(1L);
        verify(statistics, times(1)).findByManufacturer_Game_IdOrderByTradeDateAscIdAsc(1L);
        verify(investments, times(1)).findAllByManufacturer_Game_Id(1L);
        verify(business, times(1)).findAllByProductionParametersIdInAndNextDateAfter(List.of(101L, 201L), 4);
        verifyNoMoreInteractions(manufacturers, productions, advertisements, statistics, investments, business);
    }

    private Manufacturer player(long id, long accountId, Game game) {
        return Manufacturer.builder().id(id).game(game).account(Account.builder().id(accountId).build())
            .enteredInitialProductionParameters(true).balance(BigDecimal.TEN).currentProductCount(0)
            .productionCapacityPerDay(10).build();
    }

    private ProductionParameters cycle(long id, Manufacturer manufacturer, BigDecimal price) {
        return ProductionParameters.builder().id(id).manufacturer(manufacturer).startDate(0).endDate(1)
            .timeToMarket(1).productCount(10).productionCapacityPerDay(10).price(price)
            .qualityIndex(BigDecimal.ONE).assortment(1).build();
    }
}
