package ru.itis.diploma.experiment.bot.policy;

import ru.itis.diploma.game.dto.CommonProductionParameters;
import ru.itis.diploma.game.dto.StrategySnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PlanNormalizer {
    private PlanNormalizer() {}

    public enum Profile { BASIC, EXPANDED, RECOMMENDATION }

    public static CommonProductionParameters normalize(StrategySnapshot snapshot, Profile profile, int count,
                                                        BigDecimal price, BigDecimal quality, int assortment,
                                                        int ads, int days) {
        return normalize(snapshot, profile, count, price, quality, assortment, ads, days,
            snapshot.capacity() == null ? 10 : snapshot.capacity());
    }

    static CommonProductionParameters normalize(StrategySnapshot snapshot, Profile profile, int count,
                                                 BigDecimal price, BigDecimal quality, int assortment,
                                                 int ads, int days, int effectiveCapacity) {
        if (profile == Profile.EXPANDED) {
            int capacity = Math.max(1, effectiveCapacity);
            count = bounded(count, 1, capacity * 6);
            quality = quality.max(new BigDecimal("0.20")).min(BigDecimal.ONE);
            assortment = bounded(assortment, 1, count);
            ads = bounded(ads, 0, 7);
            days = ads == 0 ? 0 : bounded(days == 0 ? 1 : days, 1, (count + capacity - 1) / capacity);
            price = price.max(snapshot.baseCost().multiply(quality).multiply(new BigDecimal("1.05")))
                .max(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
        } else {
            if (profile == Profile.BASIC) assortment = Math.min(count, assortment);
            days = ads == 0 ? 0 : Math.max(1, days);
        }
        var plan = new CommonProductionParameters();
        plan.setProductCount(count);
        plan.setPrice(price);
        plan.setQualityIndex(quality);
        plan.setAssortment(assortment);
        plan.setAdvertisingIntensityIndex(ads);
        plan.setAdvertisingDays(days);
        return plan;
    }

    private static int bounded(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
