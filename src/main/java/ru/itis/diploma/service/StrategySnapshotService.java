package ru.itis.diploma.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.diploma.dto.StrategySnapshot;
import ru.itis.diploma.model.BusinessCreditPayment;
import ru.itis.diploma.model.InvestmentCreditPayment;
import ru.itis.diploma.model.Manufacturer;
import ru.itis.diploma.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StrategySnapshotService {
    private final ManufacturerRepository manufacturers;
    private final ProductionParametersRepository productions;
    private final AdvertisementRepository advertisements;
    private final StatisticsInfoRepository statistics;
    private final InvestmentCreditPaymentRepository investments;
    private final BusinessCreditPaymentRepository business;
    private final ManufacturerService manufacturerService;

    @Transactional(readOnly = true)
    public Manufacturer owner(Long gameId, Long accountId) {
        Manufacturer manufacturer = manufacturers.findByAccount_IdAndGame_Id(accountId, gameId);
        if (manufacturer == null) throw new AccessDeniedException("Вы не участвуете в этой игре");
        return manufacturer;
    }

    @Transactional(readOnly = true)
    public StrategySnapshot snapshot(Long gameId, Long accountId) {
        return snapshot(gameId, accountId, null);
    }

    /** Public competitor attributes are shared across a game's pre-decision snapshots. */
    @Transactional(readOnly = true)
    public Map<Long, StrategySnapshot.Competitor> visibleCompetitors(List<Manufacturer> players) {
        Map<Long, StrategySnapshot.Competitor> visible = new LinkedHashMap<>();
        for (Manufacturer player : players) {
            if (!player.isEnteredInitialProductionParameters()) continue;
            var last = productions.findFirstByManufacturerIdOrderByStartDateDescIdDesc(player.getId()).orElse(null);
            if (last == null || last.getPrice() == null) continue;
            var ad = advertisements.findByManufacturerIdAndStartDate(player.getId(), last.getStartDate() + 1);
            visible.put(player.getId(), new StrategySnapshot.Competitor(last.getPrice(), last.getQualityIndex(),
                last.getAssortment(), ad.map(a -> a.getIntensityIndex()).orElse(0)));
        }
        return visible;
    }

    @Transactional(readOnly = true)
    public StrategySnapshot snapshot(Long gameId, Long accountId,
                                     Map<Long, StrategySnapshot.Competitor> visibleCompetitors) {
        Manufacturer own = owner(gameId, accountId);
        var game = own.getGame();
        var last = productions.findFirstByManufacturerIdOrderByStartDateDescIdDesc(own.getId()).orElse(null);
        var ad = advertisements.findFirstByManufacturerIdOrderByStartDateDescIdDesc(own.getId()).orElse(null);
        List<StrategySnapshot.Sale> sales = statistics.findByManufacturerIdOrderByTradeDateDesc(
                own.getId(), PageRequest.of(0, 7))
            .stream().map(s -> new StrategySnapshot.Sale(s.getTradeDate(), s.getProductsSold(),
                s.getProductsProduced(), s.getProductsInStock())).toList();
        List<StrategySnapshot.Competitor> competitors = visibleCompetitors == null ?
            manufacturerService.getCompetitorsData(own.getId()).stream()
                .filter(p -> p.getPrice() != null)
                .map(p -> new StrategySnapshot.Competitor(p.getPrice(), p.getQualityIndex(),
                    p.getAssortment(), p.getAdvertisingIntensityIndex())).toList() :
            visibleCompetitors.entrySet().stream().filter(e -> !e.getKey().equals(own.getId()))
                .map(Map.Entry::getValue).toList();
        return new StrategySnapshot(gameId, game.getCurrentDay(), own.isEnteredInitialProductionParameters(),
            own.getBalance(), own.getCurrentProductCount(), game.getBaseCostPrice(), game.getBaseAdvertisementPrice(),
            game.getProductPower(), game.getSalesTax(), game.getInterestRateInvestmentCredit(),
            game.getInterestRateBusinessCredit(), game.getInvestmentCreditTermMonths() == null ? null :
                game.getInvestmentCreditTermMonths().intValue(), own.getProductionCapacityPerDay(),
            last == null ? null : last.getProductCount(), last == null ? null : last.getAssortment(),
            last == null ? null : last.getQualityIndex(), last == null ? null : last.getPrice(),
            ad == null ? 0 : ad.getIntensityIndex(), last == null ? 0 : last.getTimeToMarket(),
            sales, competitors, payments(own), own.getSelectedStrategy());
    }

    @Transactional(readOnly = true)
    public List<StrategySnapshot.Sale> allDailySales(Long gameId, Long accountId) {
        return statistics.findByManufacturerIdOrderByTradeDateDesc(owner(gameId, accountId).getId()).stream()
            .map(s -> new StrategySnapshot.Sale(s.getTradeDate(), s.getProductsSold(),
                s.getProductsProduced(), s.getProductsInStock())).toList();
    }

    private List<StrategySnapshot.Payment> payments(Manufacturer own) {
        int day = own.getGame().getCurrentDay();
        List<StrategySnapshot.Payment> result = new ArrayList<>();
        for (InvestmentCreditPayment payment : investments.findAllByManufacturerId(own.getId())) {
            if (payment.getNextDate() != null && payment.getNextDate() > day &&
                own.getInvestmentCreditDebt() != null && own.getInvestmentCreditDebt().signum() > 0) {
                // Estimate from the same visible rate and term used in the payment service.
                BigDecimal months = own.getInvestmentCreditTermMonths();
                if (months != null && months.signum() > 0) {
                    BigDecimal principal = own.getInvestmentCreditDebt().divide(months, 2, RoundingMode.UP);
                    BigDecimal interest = own.getInvestmentCreditDebt()
                        .multiply(own.getGame().getInterestRateInvestmentCredit())
                        .divide(BigDecimal.valueOf(1200), 2, RoundingMode.UP);
                    result.add(new StrategySnapshot.Payment(payment.getNextDate(), principal.add(interest),
                        "Инвестиционный кредит (оценка; сумма может измениться)"));
                }
            }
        }
        var ids = productions.findByManufacturerId(own.getId()).stream().map(p -> p.getId()).toList();
        if (!ids.isEmpty()) {
            for (BusinessCreditPayment payment : business.findAllByProductionParametersIdInAndNextDateAfter(ids, day)) {
                if (payment.getNextAmount() != null) result.add(new StrategySnapshot.Payment(payment.getNextDate(),
                    payment.getNextAmount(), "Оборотный кредит"));
            }
        }
        result.sort(Comparator.comparingInt(StrategySnapshot.Payment::day));
        return List.copyOf(result);
    }
}
