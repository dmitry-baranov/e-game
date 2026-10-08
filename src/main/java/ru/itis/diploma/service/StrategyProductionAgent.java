package ru.itis.diploma.service;

import org.springframework.stereotype.Component;
import ru.itis.diploma.dto.StrategyAdvice;
import ru.itis.diploma.dto.StrategySnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class StrategyProductionAgent {
    public void evaluate(StrategySnapshot snapshot, StrategyAdvice.Card card) {
        int count = card.getProductCount();
        int capacity = card.getCapacity();
        card.setCycleDays((count + capacity - 1) / capacity);
        if (card.getAdvertisingIntensity() > 0 && card.getAdvertisingDays() > card.getCycleDays())
            card.setAdvertisingDays(card.getCycleDays());
        BigDecimal perUnit = unitCost(snapshot.baseCost(), card);
        BigDecimal advertising = snapshot.baseAdvertising()
            .multiply(BigDecimal.valueOf(card.getAdvertisingIntensity()))
            .multiply(BigDecimal.valueOf(card.getAdvertisingDays()));
        BigDecimal equipment = snapshot.opened() ? BigDecimal.ZERO : snapshot.powerCost()
            .multiply(BigDecimal.valueOf((capacity + 9) / 10));
        card.setCosts(perUnit.multiply(BigDecimal.valueOf(count)).add(advertising).add(equipment)
            .setScale(2, RoundingMode.HALF_UP));
    }

    public BigDecimal unitCost(BigDecimal base, StrategyAdvice.Card card) {
        double perType = (double) card.getProductCount() / card.getAssortment();
        BigDecimal coefficient = perType > 1000 ? new BigDecimal("0.7") :
            perType > 100 ? new BigDecimal("0.9") : BigDecimal.ONE;
        return base.multiply(card.getQuality()).multiply(coefficient).setScale(2, RoundingMode.HALF_UP);
    }
}
