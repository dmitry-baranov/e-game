package ru.itis.diploma.experiment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itis.diploma.dto.*;
import ru.itis.diploma.service.StrategyCoordinator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.SplittableRandom;

@Component @RequiredArgsConstructor
public class BotPolicies {
    private final StrategyCoordinator coordinator;
    public static final String[] NAMES = {"CAUTIOUS", "PRICE", "QUALITY", "STOCK", "EXPLORE", "RULES"};

    public record Decision(CommonProductionParameters action, boolean usedModel, boolean fallback) {}

    public Decision decide(StrategySnapshot s, String policy, long seed, int index, String expectedModelVersion) {
        var rng = new SplittableRandom(seed ^ (index * 0x9e3779b97f4a7c15L) ^ ((long) s.day() << 32));
        if ("RULES".equals(policy) || "EVAL_RULES".equals(policy) || "MODEL".equals(policy)) {
            var cards = coordinator.recommend(s, null).getRecommendations();
            if (!cards.isEmpty()) {
                var initialKey = "RULES".equals(policy) ? "CAUTIOUS" : "QUALITY";
                var card = "MODEL".equals(policy) && s.opened() ? cards.get(0) :
                    cards.stream().filter(c -> (s.opened() ? "HOLD" : initialKey).equals(c.getKey()))
                        .findFirst().orElse(cards.get(0));
                boolean model = "MODEL".equals(policy) && s.opened() && cards.size() > 1 &&
                    cards.stream().allMatch(c -> "MODEL".equals(c.getSource()) && c.getLowCash() != null);
                if (model && !expectedModelVersion.equals(card.getModelVersion()))
                    throw new IllegalStateException("Версия модели изменилась во время оценки");
                if ("MODEL".equals(policy) && !model)
                    card = cards.stream().filter(c -> (s.opened() ? "HOLD" : initialKey).equals(c.getKey()))
                        .findFirst().orElse(cards.get(0));
                return new Decision(plan(card.getProductCount(), card.getPrice(), card.getQuality(), card.getAssortment(),
                    card.getAdvertisingIntensity(), card.getAdvertisingDays()), model, "MODEL".equals(policy) && !model);
            }
        }
        int capacity = s.capacity() == null ? 10 + index % 5 : s.capacity();
        int count = s.productCount() == null ? capacity * 3 : s.productCount();
        BigDecimal quality = s.quality() == null ? new BigDecimal("0.6") : s.quality();
        int assortment = s.assortment() == null ? 2 : s.assortment();
        int ads = 0;
        switch (policy) {
            case "CAUTIOUS": count = capacity * 2; quality = new BigDecimal("0.5"); break;
            case "PRICE":
                count = capacity * 3;
                break;
            case "QUALITY": quality = new BigDecimal("0.85"); assortment = 4; ads = 2; break;
            case "STOCK":
                count = Math.max(capacity, Math.min(capacity * 5, count + (s.stock() > count ? -capacity : capacity)));
                break;
            case "EXPLORE":
                count = capacity * rng.nextInt(1, 6); quality = BigDecimal.valueOf(rng.nextInt(4, 10), 1);
                assortment = rng.nextInt(1, 5); ads = rng.nextInt(3); break;
            default: break;
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
        return new Decision(plan(count, price, quality, Math.min(count, assortment), ads, days), false, false);
    }

    private CommonProductionParameters plan(int count, BigDecimal price, BigDecimal quality, int assortment, int ads, int days) {
        var p = new CommonProductionParameters();
        p.setProductCount(count); p.setPrice(price); p.setQualityIndex(quality);
        p.setAssortment(assortment); p.setAdvertisingIntensityIndex(ads);
        p.setAdvertisingDays(ads == 0 ? 0 : Math.max(1, days));
        return p;
    }
}
