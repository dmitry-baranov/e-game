package ru.itis.diploma.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.diploma.model.Advertisement;
import ru.itis.diploma.model.Game;
import ru.itis.diploma.model.Manufacturer;
import ru.itis.diploma.model.ProductionParameters;
import ru.itis.diploma.model.StatisticsInfo;
import ru.itis.diploma.repository.StatisticsInfoRepository;
import ru.itis.diploma.repository.GameRepository;
import ru.itis.diploma.model.enums.GameStatus;
import ru.itis.diploma.repository.TradingSessionResultsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TradingSessionService {

    private final ManufacturerService manufacturerService;
    private final TradingSessionResultsRepository tradingSessionResultsRepository;
    private final BuyerService buyerService;
    private final PaymentService paymentService;
    private final StatisticsInfoRepository statisticsInfoRepository;
    private final GameRepository gameRepository;

    @Transactional
    public void doDaysActivities(Game requestedGame) {
        Game game = gameRepository.lockById(requestedGame.getId()).orElseThrow();
        if (game.getStatus() != GameStatus.STARTED ||
            !game.getCurrentDay().equals(requestedGame.getCurrentDay())) return;
        game.setCurrentDay(game.getCurrentDay() + 1);
        Map<Long, StatisticsInfo> statistics = new HashMap<>();
        var manufacturers = manufacturerService.getGameManufacturers(game.getId());
        var purchaseCountsByManufacturerId = getPurchaseCountsByManufacturerId(
            manufacturers.stream()
                .map(Manufacturer::getId)
                .toList(), game.getHabitTrackingDays(), game.getCurrentDay());
        var productionParametersList = manufacturers.stream()
            .map(manufacturer -> manufacturerService.getLastProductionParameters(manufacturer.getId()).get())
            .sorted(Comparator.comparingDouble(p -> {
                Integer manufacturerPurchaseCounts = purchaseCountsByManufacturerId.get(((ProductionParameters) p)
                    .getManufacturer().getId());
                if (manufacturerPurchaseCounts == null) {
                    manufacturerPurchaseCounts = 0;
                }
                return calculateValue(game, (ProductionParameters) p, manufacturerPurchaseCounts);
            }).reversed())
            .toList();
        produceManufacturersProductsToMarket(game, productionParametersList, statistics);
        buyerService.makePurchases(game, productionParametersList, statistics);
        paymentService.makePayments(game, statistics);
        setCurrentCreditDebtsAndBalanceToStatisticsInfo(game, statistics);
        statisticsInfoRepository.saveAll(statistics.values());
        gameRepository.save(game);
    }

    private void setCurrentCreditDebtsAndBalanceToStatisticsInfo(Game game, Map<Long, StatisticsInfo> statistics) {
        var manufacturers = manufacturerService.getGameManufacturers(game.getId());
        manufacturers.forEach(m -> {
            StatisticsInfo statisticsInfo = statistics.get(m.getId());
            statisticsInfo.setProductsInStock(m.getCurrentProductCount());
            statisticsInfo.setBalance(m.getBalance());
            statisticsInfo.setCurrentInvestmentCreditDebtAmount(manufacturerService.calculateManufacturerInvestmentCreditDebt(m, game));
            statisticsInfo.setCurrentBusinessCreditDebtAmount(manufacturerService.calculateManufacturerBusinessCreditDebt(m));
        });
    }

    private void produceManufacturersProductsToMarket(Game game, List<ProductionParameters> productionParametersList,
                                                       Map<Long, StatisticsInfo> statistics) {
        for (ProductionParameters productionParameters : productionParametersList) {
            var statisticsInfo = new StatisticsInfo();
            statisticsInfo.setManufacturer(productionParameters.getManufacturer());
            statisticsInfo.setProductionCapacityPerDay(productionParameters.getProductionCapacityPerDay());
            var timeToMarket = productionParameters.getTimeToMarket();
            var productsProduced = 0;
            Manufacturer manufacturer = productionParameters.getManufacturer();
            if ((game.getCurrentDay() - productionParameters.getStartDate()) <= timeToMarket) {
                if ((productionParameters.getStartDate() + timeToMarket) == game.getCurrentDay()) {
                    productsProduced = productionParameters.getProductCount() - (timeToMarket - 1) *
                        productionParameters.getProductionCapacityPerDay();
                } else {
                    productsProduced = productionParameters.getProductionCapacityPerDay();
                }
            }
            manufacturer.setCurrentProductCount(manufacturer.getCurrentProductCount() + productsProduced);
            statisticsInfo.setProductsProduced(productsProduced);
            statisticsInfo.setTradeDate(game.getCurrentDay());
            statisticsInfo.setPaidTaxesAmount(BigDecimal.ZERO);
            statisticsInfo.setCurrentInvestmentCreditDebtAmount(BigDecimal.ZERO);
            statisticsInfo.setCurrentBusinessCreditDebtAmount(BigDecimal.ZERO);
            statisticsInfo.setRepaidInvestmentCreditAmount(BigDecimal.ZERO);
            statisticsInfo.setRepaidBusinessCreditAmount(BigDecimal.ZERO);
            statistics.put(manufacturer.getId(), statisticsInfo);
        }
    }

    public Map<Long, Integer> getPurchaseCountsByManufacturerId(List<Long> manufacturerIds, int habitTrackingDays, int currentDay) {
        int startDate = currentDay - habitTrackingDays > 0 ? currentDay - habitTrackingDays : 1;
        Map<Long, Integer> counts = new HashMap<>();
        if (manufacturerIds.isEmpty()) return counts;
        for (Object[] row : tradingSessionResultsRepository.sumProductsByManufacturerSince(startDate, manufacturerIds))
            counts.put((Long) row[0], Math.toIntExact((Long) row[1]));
        return counts;
    }

    private double calculateValue(Game game, ProductionParameters productionParameters, int manufacturerPurchaseCounts) {
        BigDecimal assortmentWeight = game.getAssortmentWeight();
        BigDecimal qualityWeight = game.getQualityWeight();
        BigDecimal advertisementWeight = game.getAdvertisementWeight();
        BigDecimal habitWeight = game.getHabitWeight();

        var habit = (double) manufacturerPurchaseCounts / (game.getPurchaseLimit() * game.getHabitTrackingDays());
        Optional<Advertisement> lastAdvertisement = manufacturerService.getLastAdvertisement(productionParameters.getManufacturer().getId());
        var intensityIndex = lastAdvertisement.isPresent() ? lastAdvertisement.get().getIntensityIndex() : 0;
        return productionParameters.getQualityIndex().multiply(qualityWeight)
            .add(BigDecimal.valueOf(intensityIndex).divide(BigDecimal.valueOf(7), 3, RoundingMode.HALF_UP))
            .multiply(advertisementWeight)
            .add(BigDecimal.valueOf(productionParameters.getAssortment())
                .divide(BigDecimal.valueOf(productionParameters.getProductCount()), 3, RoundingMode.HALF_UP))
            .multiply(assortmentWeight)
            .add(BigDecimal.valueOf(habit).multiply(habitWeight))
            .divide(productionParameters.getPrice(), 6, RoundingMode.HALF_UP).doubleValue();
    }

}
