package ru.itis.diploma.experiment.bot.policy;

import ru.itis.diploma.game.dto.StrategySnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;

final class BasicPolicies {
    PolicyDecision decide(PolicyContext context, String policy) {
        StrategySnapshot s = context.snapshot();
        var rng = context.random();
        int capacity = s.capacity() == null ? 10 + context.index() % 5 : s.capacity();
        int count = s.productCount() == null ? capacity * 3 : s.productCount();
        BigDecimal quality = s.quality() == null ? new BigDecimal("0.6") : s.quality();
        int assortment = s.assortment() == null ? 2 : s.assortment();
        int ads = 0;
        switch (policy) {
            case "CAUTIOUS" -> { count = capacity * 2; quality = new BigDecimal("0.5"); }
            case "PRICE" -> count = capacity * 3;
            case "QUALITY" -> { quality = new BigDecimal("0.85"); assortment = 4; ads = 2; }
            case "STOCK" -> count = Math.max(capacity,
                Math.min(capacity * 5, count + (s.stock() > count ? -capacity : capacity)));
            case "EXPLORE" -> {
                count = capacity * rng.nextInt(1, 6);
                quality = BigDecimal.valueOf(rng.nextInt(4, 10), 1);
                assortment = rng.nextInt(1, 5);
                ads = rng.nextInt(3);
            }
            case "RULES", "EVAL_RULES", "MODEL" -> { }
            default -> throw unknown(policy);
        }
        BigDecimal cost = s.baseCost().multiply(quality);
        BigDecimal price = cost.multiply(new BigDecimal("1.8"));
        if ("PRICE".equals(policy) && !s.competitors().isEmpty()) {
            BigDecimal cheapest = s.competitors().stream().map(StrategySnapshot.Competitor::price)
                .min(Comparator.naturalOrder()).orElse(price);
            price = cheapest.multiply(new BigDecimal("0.95"));
        }
        if ("EXPLORE".equals(policy)) price = cost.multiply(BigDecimal.valueOf(rng.nextInt(12, 26), 1));
        price = price.max(cost.multiply(new BigDecimal("1.05"))).setScale(2, RoundingMode.HALF_UP);
        int days = ads == 0 ? 0 : Math.min(2, (count + capacity - 1) / capacity);
        return new PolicyDecision(PlanNormalizer.normalize(s, PlanNormalizer.Profile.BASIC,
            count, price, quality, assortment, ads, days));
    }

    private static IllegalArgumentException unknown(String policy) {
        return new IllegalArgumentException("Неизвестная политика бота: " + policy);
    }
}
