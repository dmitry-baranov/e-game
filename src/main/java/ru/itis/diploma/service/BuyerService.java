package ru.itis.diploma.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.itis.diploma.model.Game;
import ru.itis.diploma.model.Manufacturer;
import ru.itis.diploma.model.ProductionParameters;
import ru.itis.diploma.model.StatisticsInfo;
import ru.itis.diploma.model.TradingSessionResults;
import ru.itis.diploma.repository.ManufacturerRepository;
import ru.itis.diploma.repository.TradingSessionResultsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Slf4j
@Service
@RequiredArgsConstructor
public class BuyerService {

    private final ManufacturerRepository manufacturerRepository;
    private final TradingSessionResultsRepository tradingSessionResultsRepository;

    private static final Logger logger = LoggerFactory.getLogger(BuyerService.class);

    public void makePurchases(Game game, List<ProductionParameters> productionParametersList,
                              Map<Long, StatisticsInfo> statistics) {
        updateWornOutFlag(game);
        BigDecimal dailySpendingLimit = game.getDailySpendingLimit();
        int requiredQuantity = game.getPurchaseLimit() - getUnwornProductsCount(game);
        List<TradingSessionResults> tradingSessionResultsList = new ArrayList<>();
        List<Manufacturer> manufacturers = new ArrayList<>();

        logger.info("НАЧИНАЮ СОВЕРШАТЬ ПОКУПКИ...");
        for (ProductionParameters productionParameters : productionParametersList) {
            var manufacturer = productionParameters.getManufacturer();
            var manufacturerCurrentProductCount = manufacturer.getCurrentProductCount();

            int purchaseQuantity = calculatePurchaseQuantity(dailySpendingLimit, productionParameters.getPrice(),
                requiredQuantity, manufacturerCurrentProductCount);

            TradingSessionResults tradingSessionResults = TradingSessionResults.builder()
                .manufacturer(manufacturer)
                .productNumber(purchaseQuantity)
                .price(productionParameters.getPrice())
                .qualityIndex(productionParameters.getQualityIndex())
                .tradeDate(game.getCurrentDay())
                .isWornOut(false)
                .build();
            tradingSessionResultsList.add(tradingSessionResults);
            StatisticsInfo statisticsInfo = statistics.get(manufacturer.getId());
            statisticsInfo.setProductsSold(purchaseQuantity);
            statisticsInfo.setPrice(productionParameters.getPrice());

            if (purchaseQuantity > 0) {
                logger.info("ПОКУПАЮ {} ТОВАРОВ", purchaseQuantity);
                BigDecimal totalPrice = productionParameters.getPrice().multiply(BigDecimal.valueOf(purchaseQuantity));
                manufacturer.setCurrentProductCount(manufacturerCurrentProductCount - purchaseQuantity);
                manufacturer.setBalance(manufacturer.getBalance().add(totalPrice));
                manufacturers.add(manufacturer);
                requiredQuantity -= purchaseQuantity;
                dailySpendingLimit = dailySpendingLimit.subtract(totalPrice);
            }
            logger.info("ОСТАЛОСЬ КУПИТЬ - {} ", requiredQuantity);
        }
        manufacturerRepository.saveAll(manufacturers);
        tradingSessionResultsRepository.saveAll(tradingSessionResultsList);
    }

    private int calculatePurchaseQuantity(BigDecimal dailySpendingLimit,
                                          BigDecimal productPrice,
                                          int requiredQuantity,
                                          int manufacturerCurrentProductCount) {
        int purchaseQuantity = Math.min(requiredQuantity, manufacturerCurrentProductCount);
        int maxQuantityBySpendingLimit = dailySpendingLimit.divide(productPrice, RoundingMode.FLOOR).intValue();
        return Math.min(purchaseQuantity, maxQuantityBySpendingLimit);
    }

    private void updateWornOutFlag(Game game) {
        tradingSessionResultsRepository.markWornOutByGameId(game.getId(), game.getCurrentDay(),
            game.getAbsoluteQualityProductLife());
    }

    private Integer getUnwornProductsCount(Game game) {
        Integer unwornProductsCount = tradingSessionResultsRepository.getUnwornProductsCountByGameId(game.getId());
        if (unwornProductsCount == null) return 0;
        return unwornProductsCount;
    }

}
