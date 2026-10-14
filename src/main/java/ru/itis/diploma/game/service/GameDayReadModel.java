package ru.itis.diploma.game.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.diploma.game.dto.StrategySnapshot;
import ru.itis.diploma.game.model.*;
import ru.itis.diploma.game.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** A fixed-query, pre-decision view of every manufacturer in one game day. */
@Service
@RequiredArgsConstructor
public class GameDayReadModel {
    private final ManufacturerRepository manufacturers;
    private final ProductionParametersRepository productions;
    private final AdvertisementRepository advertisements;
    private final StatisticsInfoRepository statistics;
    private final InvestmentCreditPaymentRepository investments;
    private final BusinessCreditPaymentRepository business;

    /** Snapshots are keyed by account id and are all built from the same persisted game-day state. */
    @Transactional(readOnly = true)
    public Map<Long, StrategySnapshot> snapshotsBeforeDecisions(long gameId) {
        var players = manufacturers.findGamePlayers(gameId);
        if (players.isEmpty()) return Map.of();
        var cycles = productions.findByManufacturer_Game_IdOrderByStartDateAscIdAsc(gameId);
        var ads = advertisements.findByManufacturer_Game_IdOrderByStartDateAscIdAsc(gameId);
        var daily = statistics.findByManufacturer_Game_IdOrderByTradeDateAscIdAsc(gameId);
        var credit = investments.findAllByManufacturer_Game_Id(gameId);
        var cycleIds = cycles.stream().map(ProductionParameters::getId).filter(Objects::nonNull).toList();
        var operating = cycleIds.isEmpty() ? List.<BusinessCreditPayment>of() :
            business.findAllByProductionParametersIdInAndNextDateAfter(cycleIds, players.getFirst().getGame().getCurrentDay());

        Map<Long, List<ProductionParameters>> cyclesByPlayer = group(cycles, p -> p.getManufacturer().getId());
        Map<Long, List<Advertisement>> adsByPlayer = group(ads, a -> a.getManufacturer().getId());
        Map<Long, List<StatisticsInfo>> dailyByPlayer = group(daily, s -> s.getManufacturer().getId());
        Map<Long, List<InvestmentCreditPayment>> creditByPlayer = group(credit, p -> p.getManufacturer().getId());
        Map<Long, List<BusinessCreditPayment>> operatingByPlayer = group(operating,
            p -> p.getProductionParameters().getManufacturer().getId());
        Map<Long, StrategySnapshot.Competitor> visible = visible(players, cyclesByPlayer, adsByPlayer);
        Map<Long, StrategySnapshot> result = new LinkedHashMap<>();
        for (var player : players) {
            var ownCycles = cyclesByPlayer.getOrDefault(player.getId(), List.of());
            var ownAds = adsByPlayer.getOrDefault(player.getId(), List.of());
            var last = ownCycles.isEmpty() ? null : ownCycles.getLast();
            var lastAd = ownAds.isEmpty() ? null : ownAds.getLast();
            var ownDaily = dailyByPlayer.getOrDefault(player.getId(), List.of());
            var sales = ownDaily.reversed().stream().limit(7).map(s -> new StrategySnapshot.Sale(
                s.getTradeDate(), value(s.getProductsSold()), value(s.getProductsProduced()), value(s.getProductsInStock()))).toList();
            var competitors = visible.entrySet().stream().filter(e -> !e.getKey().equals(player.getId()))
                .map(Map.Entry::getValue).toList();
            result.put(player.getAccount().getId(), snapshot(player, last, lastAd, sales, competitors,
                creditByPlayer.getOrDefault(player.getId(), List.of()),
                operatingByPlayer.getOrDefault(player.getId(), List.of())));
        }
        return Collections.unmodifiableMap(result);
    }

    @Transactional(readOnly = true)
    public Map<Long, StrategySnapshot.Competitor> visibleCompetitors(long gameId) {
        var players = manufacturers.findGamePlayers(gameId);
        var cycles = productions.findByManufacturer_Game_IdOrderByStartDateAscIdAsc(gameId);
        var ads = advertisements.findByManufacturer_Game_IdOrderByStartDateAscIdAsc(gameId);
        return Collections.unmodifiableMap(visible(players, group(cycles, p -> p.getManufacturer().getId()),
            group(ads, a -> a.getManufacturer().getId())));
    }

    private StrategySnapshot snapshot(Manufacturer own, ProductionParameters last, Advertisement ad,
                                      List<StrategySnapshot.Sale> sales,
                                      List<StrategySnapshot.Competitor> competitors,
                                      List<InvestmentCreditPayment> investments,
                                      List<BusinessCreditPayment> business) {
        var game = own.getGame();
        return new StrategySnapshot(game.getId(), game.getCurrentDay(), own.isEnteredInitialProductionParameters(),
            own.getBalance(), own.getCurrentProductCount(), game.getBaseCostPrice(), game.getBaseAdvertisementPrice(),
            game.getProductPower(), game.getSalesTax(), game.getInterestRateInvestmentCredit(),
            game.getInterestRateBusinessCredit(), game.getInvestmentCreditTermMonths() == null ? null :
            game.getInvestmentCreditTermMonths().intValue(), own.getProductionCapacityPerDay(),
            last == null ? null : last.getProductCount(), last == null ? null : last.getAssortment(),
            last == null ? null : last.getQualityIndex(), last == null ? null : last.getPrice(),
            ad == null ? 0 : ad.getIntensityIndex(), last == null ? 0 : last.getTimeToMarket(), sales,
            competitors, payments(own, investments, business), own.getSelectedStrategy());
    }

    private Map<Long, StrategySnapshot.Competitor> visible(List<Manufacturer> players,
            Map<Long, List<ProductionParameters>> cycles, Map<Long, List<Advertisement>> ads) {
        Map<Long, StrategySnapshot.Competitor> result = new LinkedHashMap<>();
        for (var player : players) {
            if (!player.isEnteredInitialProductionParameters()) continue;
            var ownCycles = cycles.getOrDefault(player.getId(), List.of());
            if (ownCycles.isEmpty() || ownCycles.getLast().getPrice() == null) continue;
            var last = ownCycles.getLast();
            var ad = ads.getOrDefault(player.getId(), List.of()).stream()
                .filter(a -> Objects.equals(a.getStartDate(), last.getStartDate() + 1)).reduce((a, b) -> b).orElse(null);
            result.put(player.getId(), new StrategySnapshot.Competitor(last.getPrice(), last.getQualityIndex(),
                last.getAssortment(), ad == null ? 0 : ad.getIntensityIndex()));
        }
        return result;
    }

    private List<StrategySnapshot.Payment> payments(Manufacturer own, List<InvestmentCreditPayment> investments,
                                                     List<BusinessCreditPayment> business) {
        int day = own.getGame().getCurrentDay();
        List<StrategySnapshot.Payment> result = new ArrayList<>();
        for (var payment : investments) {
            if (payment.getNextDate() != null && payment.getNextDate() > day && own.getInvestmentCreditDebt() != null &&
                own.getInvestmentCreditDebt().signum() > 0 && own.getInvestmentCreditTermMonths() != null &&
                own.getInvestmentCreditTermMonths().signum() > 0) {
                var principal = own.getInvestmentCreditDebt().divide(own.getInvestmentCreditTermMonths(), 2, RoundingMode.UP);
                var interest = own.getInvestmentCreditDebt().multiply(own.getGame().getInterestRateInvestmentCredit())
                    .divide(BigDecimal.valueOf(1200), 2, RoundingMode.UP);
                result.add(new StrategySnapshot.Payment(payment.getNextDate(), principal.add(interest),
                    "Инвестиционный кредит (оценка; сумма может измениться)"));
            }
        }
        for (var payment : business)
            if (payment.getNextAmount() != null)
                result.add(new StrategySnapshot.Payment(payment.getNextDate(), payment.getNextAmount(), "Оборотный кредит"));
        result.sort(Comparator.comparingInt(StrategySnapshot.Payment::day));
        return List.copyOf(result);
    }

    private static int value(Integer value) { return value == null ? 0 : value; }
    private static <T> Map<Long, List<T>> group(List<T> values, Function<T, Long> key) {
        return values.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
    }
}
