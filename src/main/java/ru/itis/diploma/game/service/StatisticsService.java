package ru.itis.diploma.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.itis.diploma.game.dto.AdvertisingIntensity;
import ru.itis.diploma.game.model.TradingSessionResults;
import ru.itis.diploma.game.repository.AdvertisementRepository;
import ru.itis.diploma.game.repository.TradingSessionResultsRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final ManufacturerService manufacturerService;
    private final TradingSessionResultsRepository tradingSessionResultsRepository;
    private final AdvertisementRepository advertisementRepository;

    public Map<Integer, BigDecimal> getRevenueData(Long manufacturerId) {
        return tradingSessionResultsRepository.findByManufacturerId(
                manufacturerId)
            .stream()
            .collect(Collectors.toMap(
                TradingSessionResults::getTradeDate,
                val -> val.getPrice().multiply(BigDecimal.valueOf(val.getProductNumber())))
            );
    }

    public List<AdvertisingIntensity> getAdvertisingIntensityData(Long gameId, Long accountId) {
        var manufacturer = manufacturerService.getManufacturerByAccountIdAndGameId(accountId, gameId);
        return advertisementRepository.findByManufacturerId(manufacturer.getId()).stream().map(advertisement ->
                AdvertisingIntensity.builder()
                    .startDate(advertisement.getStartDate())
                    .endDate(advertisement.getEndDate())
                    .intensityIndex(advertisement.getIntensityIndex())
                    .build())
            .toList();
    }
}
