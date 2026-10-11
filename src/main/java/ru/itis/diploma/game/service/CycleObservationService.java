package ru.itis.diploma.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.diploma.game.model.*;
import ru.itis.diploma.game.repository.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CycleObservationService {
    private final ProductionParametersRepository productions;
    private final StatisticsInfoRepository statistics;
    private final AdvertisementRepository advertisements;
    private final CycleObservationRepository observations;

    /** Called inside the trading transaction after all manufacturers' daily statistics are saved. */
    @Transactional
    public void refresh(long gameId) {
        var cycles = productions.findByManufacturer_Game_IdOrderByStartDateAscIdAsc(gameId);
        var daily = statistics.findByManufacturer_Game_IdOrderByTradeDateAscIdAsc(gameId);
        var ads = advertisements.findByManufacturer_Game_IdOrderByStartDateAscIdAsc(gameId).stream()
            .collect(Collectors.toMap(a -> a.getManufacturer().getId() + ":" + a.getStartDate(),
                Function.identity(), (first, second) -> second));
        var existing = observations.findByManufacturer_Game_Id(gameId).stream()
            .collect(Collectors.toMap(o -> o.getProductionParameters().getId(), Function.identity()));
        Map<Long, Map<Integer, StatisticsInfo>> dailyByManufacturer = new HashMap<>();
        for (var item : daily) {
            dailyByManufacturer.computeIfAbsent(item.getManufacturer().getId(), ignored -> new LinkedHashMap<>())
                .put(item.getTradeDate(), item);
        }
        var result = new java.util.ArrayList<CycleObservation>();

        for (var cycle : cycles) {
            int end = cycle.getEndDate() == null ? cycle.getStartDate() + cycle.getTimeToMarket() : cycle.getEndDate();
            if (cycle.getEndDate() == null) cycle.setEndDate(end);
            var days = new HashMap<Integer, StatisticsInfo>();
            var manufacturerDays = dailyByManufacturer.getOrDefault(cycle.getManufacturer().getId(), Map.of());
            for (var item : manufacturerDays.values()) {
                if (item.getTradeDate() > cycle.getStartDate() && item.getTradeDate() <= end)
                    days.put(item.getTradeDate(), item);
            }
            var observation = existing.getOrDefault(cycle.getId(), new CycleObservation());
            observation.setProductionParameters(cycle);
            observation.setManufacturer(cycle.getManufacturer());
            observation.setStartDate(cycle.getStartDate());
            observation.setEndDate(end);
            observation.setExpectedDays(cycle.getTimeToMarket());
            observation.setObservedDays(days.size());
            observation.setComplete(days.size() == cycle.getTimeToMarket() &&
                java.util.stream.IntStream.rangeClosed(cycle.getStartDate() + 1, end).allMatch(days::containsKey));
            observation.setProductsSold(days.values().stream().mapToInt(s -> value(s.getProductsSold())).sum());
            observation.setProductsProduced(days.values().stream().mapToInt(s -> value(s.getProductsProduced())).sum());
            observation.setRevenue(cycle.getPrice() == null ? BigDecimal.ZERO :
                cycle.getPrice().multiply(BigDecimal.valueOf(observation.getProductsSold())));
            observation.setPayments(days.values().stream().map(this::payments).reduce(BigDecimal.ZERO, BigDecimal::add));
            var last = days.get(end);
            observation.setEndingStock(last == null ? null : last.getProductsInStock());
            var before = manufacturerDays.values().stream().filter(s -> s.getTradeDate() <= cycle.getStartDate())
                .reduce((a, b) -> b).orElse(null);
            observation.setBalanceChange(last == null || before == null || last.getBalance() == null || before.getBalance() == null ?
                null : last.getBalance().subtract(before.getBalance()));
            var ad = ads.get(cycle.getManufacturer().getId() + ":" + (cycle.getStartDate() + 1));
            observation.setAdvertisingIntensity(ad == null ? 0 : ad.getIntensityIndex());
            observation.setAdvertisingDays(ad == null ? 0 : ad.getEndDate() - ad.getStartDate() + 1);
            result.add(observation);
        }
        observations.saveAll(result);
    }

    private int value(Integer value) { return value == null ? 0 : value; }

    private BigDecimal payments(StatisticsInfo item) {
        return value(item.getPaidTaxesAmount()).add(value(item.getRepaidInvestmentCreditAmount()))
            .add(value(item.getRepaidBusinessCreditAmount()));
    }

    private BigDecimal value(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
