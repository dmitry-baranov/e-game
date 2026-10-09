package ru.itis.diploma.experiment;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.itis.diploma.dto.*;
import ru.itis.diploma.service.StrategyCoordinator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.SplittableRandom;

@Component @RequiredArgsConstructor
public class BotPolicies {
    private final StrategyCoordinator coordinator;
    public static final String[] NAMES = {"CAUTIOUS", "PRICE", "QUALITY", "STOCK", "EXPLORE", "RULES",
        "PRICE_MEDIAN", "PRICE_PREMIUM", "PRICE_CUT_AFTER_ZERO", "PRICE_RAISE_AFTER_SELLOUT", "PRICE_MARGIN_GUARD",
        "PRICE_HYSTERESIS", "SALES_SMOOTH", "STOCK_CLEAR", "STOCKOUT_RECOVER", "CAPACITY_STEADY",
        "SHORT_CYCLE", "LONG_CYCLE", "QUALITY_STEP_UP", "QUALITY_BUDGET", "ASSORTMENT_BROADEN",
        "ASSORTMENT_FOCUS", "ADS_PULSE", "ADS_STOP_LOSS", "CASH_RESERVE", "DEBT_AVOID", "PAYMENT_AWARE",
        "RIVAL_QUALITY_GAP", "RIVAL_AD_GAP", "PRICE_TWO_LEVELS", "PRICE_REVENUE_WINDOW", "VOLATILITY_BUFFER",
        "STOCK_RUNWAY", "ZERO_SALES_PIVOT", "CONSERVATIVE_CASH_SCORE", "LIMITED_CREDIT_GROWTH",
        "ADS_DURATION_TEST", "QUALITY_PRICE_BUNDLE"};
    public static final Map<String, String> DESCRIPTIONS = Map.ofEntries(
        Map.entry("CAUTIOUS", "Осторожный выпуск"), Map.entry("PRICE", "Цена дешевле видимого конкурента"),
        Map.entry("QUALITY", "Высокое качество и реклама"), Map.entry("STOCK", "Выпуск по размеру склада"),
        Map.entry("EXPLORE", "Воспроизводимый перебор допустимых решений"), Map.entry("RULES", "Рекомендации по правилам"),
        Map.entry("PRICE_MEDIAN", "Цена к медиане конкурентов"), Map.entry("PRICE_PREMIUM", "Цена выше медианы конкурентов"),
        Map.entry("PRICE_CUT_AFTER_ZERO", "Снижать цену после нескольких нулевых продаж"),
        Map.entry("PRICE_RAISE_AFTER_SELLOUT", "Повышать цену после распродаж"),
        Map.entry("PRICE_MARGIN_GUARD", "Цена с запасом маржи после налога"),
        Map.entry("PRICE_HYSTERESIS", "Не менять цену при мелких колебаниях рынка"),
        Map.entry("SALES_SMOOTH", "Сглаживать выпуск по прошлым продажам"),
        Map.entry("STOCK_CLEAR", "Уменьшать выпуск при большом остатке"),
        Map.entry("STOCKOUT_RECOVER", "Увеличивать выпуск после распродаж"),
        Map.entry("CAPACITY_STEADY", "Стабильная партия по собственной мощности"),
        Map.entry("SHORT_CYCLE", "Короткие производственные циклы"),
        Map.entry("LONG_CYCLE", "Длинные циклы при устойчивом спросе"),
        Map.entry("QUALITY_STEP_UP", "Постепенно улучшать качество"),
        Map.entry("QUALITY_BUDGET", "Снижать затраты на качество при нехватке денег"),
        Map.entry("ASSORTMENT_BROADEN", "Постепенно расширять ассортимент при распродажах"),
        Map.entry("ASSORTMENT_FOCUS", "Сокращать ассортимент при слабом сбыте"),
        Map.entry("ADS_PULSE", "Чередовать рекламные импульсы и паузы"),
        Map.entry("ADS_STOP_LOSS", "Останавливать рекламу после слабого рекламного цикла"),
        Map.entry("CASH_RESERVE", "Сохранять денежный резерв"),
        Map.entry("DEBT_AVOID", "Не начинать новый цикл с оборотным кредитом"),
        Map.entry("PAYMENT_AWARE", "Резервировать деньги под известные платежи"),
        Map.entry("RIVAL_QUALITY_GAP", "Подтягивать качество к близким по цене конкурентам"),
        Map.entry("RIVAL_AD_GAP", "Реагировать на заметную рекламу конкурентов"),
        Map.entry("PRICE_TWO_LEVELS", "Чередовать два уровня цены"),
        Map.entry("PRICE_REVENUE_WINDOW", "Опираться на выручку прошлых полных циклов"),
        Map.entry("VOLATILITY_BUFFER", "Снижать выпуск при изменчивых продажах"),
        Map.entry("STOCK_RUNWAY", "Сопоставлять срок покрытия запасом и длину цикла"),
        Map.entry("ZERO_SALES_PIVOT", "Менять качество или ассортимент после двух нулевых циклов"),
        Map.entry("CONSERVATIVE_CASH_SCORE", "Сравнивать допустимые цену и партию по осторожной оценке денег"),
        Map.entry("LIMITED_CREDIT_GROWTH", "Расти с жёстким лимитом нового кредита"),
        Map.entry("ADS_DURATION_TEST", "Чередовать и сравнивать длительность рекламы"),
        Map.entry("QUALITY_PRICE_BUNDLE", "Менять качество и цену вместе"));

    public record Decision(CommonProductionParameters action, boolean usedModel, boolean fallback,
                           java.util.List<StrategyAdvice.Card> candidates, String fallbackReason) {
        public Decision(CommonProductionParameters action, boolean usedModel, boolean fallback) {
            this(action, usedModel, fallback, java.util.List.of(), null);
        }
    }

    public Decision decide(StrategySnapshot s, String policy, long seed, int index, String expectedModelVersion) {
        return decide(s, policy, seed, index, expectedModelVersion, List.of());
    }

    public Decision decide(StrategySnapshot s, String policy, long seed, int index, String expectedModelVersion,
                           List<ObservedCycle> history) {
        if (!Arrays.asList(NAMES).contains(policy) && !"EVAL_RULES".equals(policy) && !"MODEL".equals(policy))
            throw new IllegalArgumentException("Неизвестная политика бота: " + policy);
        var rng = new SplittableRandom(seed ^ (index * 0x9e3779b97f4a7c15L) ^ ((long) s.day() << 32));
        if ("RULES".equals(policy) || "EVAL_RULES".equals(policy) || "MODEL".equals(policy)) {
            var advice = coordinator.recommend(s, null);
            var cards = advice.getRecommendations();
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
                    card.getAdvertisingIntensity(), card.getAdvertisingDays()), model, "MODEL".equals(policy) && !model,
                    java.util.List.copyOf(cards), "MODEL".equals(policy) && !model ?
                        (s.opened() ? (advice.getModelFallbackReason() == null ? "NO_COMPARABLE_CARDS" :
                            advice.getModelFallbackReason()) : "STARTING_GAME") : null);
            }
        }
        if (Arrays.asList(NAMES).indexOf(policy) >= 6) return expanded(s, policy, index, history);
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

    private Decision expanded(StrategySnapshot s, String policy, int index, List<ObservedCycle> history) {
        int capacity = Math.max(1, s.capacity() == null ? 10 + index % 5 : s.capacity());
        BigDecimal balance = s.balance() == null ? BigDecimal.ZERO : s.balance();
        int count = s.productCount() == null ? capacity * 3 : s.productCount();
        BigDecimal quality = s.quality() == null ? bd("0.60") : s.quality();
        int assortment = s.assortment() == null ? 2 : s.assortment();
        BigDecimal cost = s.baseCost().multiply(quality);
        BigDecimal price = s.price() == null ? cost.multiply(bd("1.8")) : s.price();
        int ads = s.advertisingIntensity() == null ? 0 : s.advertisingIntensity();
        int adDays = 0;
        final BigDecimal ownPrice = price;
        final BigDecimal ownQuality = quality;
        final int ownAds = ads;
        double sold = s.sales().stream().mapToInt(StrategySnapshot.Sale::sold).average().orElse(0);
        long zeros = s.sales().stream().filter(d -> d.sold() == 0).count();
        long sellouts = s.sales().stream().filter(d -> d.sold() > 0 && d.stock() == 0).count();
        BigDecimal median = median(s.competitors().stream().map(StrategySnapshot.Competitor::price).toList());
        ObservedCycle last = history.isEmpty() ? null : history.get(history.size() - 1);
        int cycleNumber = history.size();
        switch (policy) {
            case "PRICE_MEDIAN" -> { if (median != null) price = step(price, median, bd("0.12")); }
            case "PRICE_PREMIUM" -> { if (median != null) price = step(price, median.multiply(bd("1.15")), bd("0.15")); }
            case "PRICE_CUT_AFTER_ZERO" -> { if (s.sales().size() >= 3 && zeros >= 3) price = price.multiply(bd("0.90")); }
            case "PRICE_RAISE_AFTER_SELLOUT" -> { if (sellouts >= 2) price = price.multiply(bd("1.08")); }
            case "PRICE_MARGIN_GUARD" -> price = cost.multiply(bd("1.35"))
                .divide(BigDecimal.ONE.subtract(s.taxRate().divide(bd("100"), 6, RoundingMode.HALF_UP)),
                    2, RoundingMode.HALF_UP);
            case "PRICE_HYSTERESIS" -> {
                if (median != null && history.size() >= 2 &&
                    history.get(history.size()-2).price().subtract(price).abs().compareTo(price.multiply(bd("0.02"))) <= 0 &&
                    price.subtract(median).abs().compareTo(median.multiply(bd("0.15"))) > 0)
                    price = step(price, median, bd("0.10"));
            }
            case "SALES_SMOOTH" -> { if (s.sales().size() >= 3)
                count = bounded((int)Math.round((sold * 3 + count / 2.0) / 2), capacity, capacity * 6); }
            case "STOCK_CLEAR" -> { if (s.stock() > count / 2) {
                count = Math.max(1, count - capacity); price = price.multiply(bd("0.95"));
            } }
            case "STOCKOUT_RECOVER" -> { if (sellouts >= 2) count += capacity; }
            case "CAPACITY_STEADY" -> count = s.stock() > capacity * 3 ? capacity * 2 : capacity * 3;
            case "SHORT_CYCLE" -> count = capacity;
            case "LONG_CYCLE" -> count = s.sales().size() >= 3 && zeros == 0 && s.stock() <= capacity ? capacity * 5 : capacity * 2;
            case "QUALITY_STEP_UP" -> { if (s.sales().size() >= 3 && zeros <= 1 &&
                balance.compareTo(cost.multiply(BigDecimal.valueOf(capacity * 2L))) > 0)
                quality = quality.add(bd("0.08")); }
            case "QUALITY_BUDGET" -> { if (s.opened() && balance.compareTo(cost.multiply(BigDecimal.valueOf(count))) < 0)
                quality = quality.subtract(bd("0.12")); }
            case "ASSORTMENT_BROADEN" -> { if (sellouts >= 2 && balance.signum() > 0) assortment += 1; }
            case "ASSORTMENT_FOCUS" -> { if (zeros >= 2 || (s.opened() && balance.signum() <= 0)) assortment -= 1; }
            case "ADS_PULSE" -> { ads = cycleNumber % 2 == 0 && (!s.opened() || balance.signum() > 0) ? 2 : 0; adDays = ads == 0 ? 0 : 1; }
            case "ADS_STOP_LOSS" -> { ads = last != null && last.advertising() > 0 && last.sold() < last.days() * 2 ? 0 : 2;
                adDays = ads == 0 ? 0 : 1; }
            case "CASH_RESERVE" -> { if (s.opened()) count = affordable(s, quality, 0, 0, balance.multiply(bd("0.7")), capacity);
                ads = 0; }
            case "DEBT_AVOID" -> { if (s.opened()) count = affordable(s, quality, 0, 0, balance, capacity);
                ads = 0; }
            case "PAYMENT_AWARE" -> { if (s.opened()) {
                BigDecimal obligations = upcoming(s, s.day() + Math.max(1, (count+capacity-1)/capacity));
                count = affordable(s, quality, 0, 0, balance.subtract(obligations), capacity);
                ads = 0;
            } }
            case "RIVAL_QUALITY_GAP" -> { if (s.competitors().stream().anyMatch(c -> c.quality() != null &&
                c.price() != null && c.price().subtract(ownPrice).abs().compareTo(ownPrice.multiply(bd("0.25"))) < 0 &&
                c.quality().subtract(ownQuality).compareTo(bd("0.15")) > 0) && balance.signum() > 0)
                quality = quality.add(bd("0.12")); }
            case "RIVAL_AD_GAP" -> { if (s.competitors().stream().anyMatch(c -> c.advertisingIntensity() != null &&
                c.advertisingIntensity() >= ownAds + 2) && balance.compareTo(s.baseAdvertising().multiply(bd("3"))) > 0)
                ads = Math.min(4, ads + 1); adDays = ads == 0 ? 0 : 1; }
            case "PRICE_TWO_LEVELS" -> price = cost.multiply(cycleNumber % 2 == 0 ? bd("1.45") : bd("1.90"));
            case "PRICE_REVENUE_WINDOW" -> { if (history.size() >= 2) {
                ObservedCycle previous = history.get(history.size()-2);
                if (Math.abs(previous.count()-last.count()) <= Math.max(capacity, last.count()/5) &&
                    previous.price().compareTo(last.price()) != 0) {
                    BigDecimal first = previous.revenue().divide(BigDecimal.valueOf(previous.days()), 2, RoundingMode.HALF_UP);
                    BigDecimal second = last.revenue().divide(BigDecimal.valueOf(last.days()), 2, RoundingMode.HALF_UP);
                    price = step(price, first.compareTo(second) > 0 ? previous.price() : last.price(), bd("0.10"));
                }
            } }
            case "VOLATILITY_BUFFER" -> { if (s.sales().size() >= 3) {
                double mean = sold;
                double variance = s.sales().stream().mapToDouble(d -> Math.pow(d.sold()-mean,2)).average().orElse(0);
                count = variance > Math.pow(Math.max(1,mean),2)/4 ? capacity * 2 : capacity * 3;
            } }
            case "STOCK_RUNWAY" -> { if (sold > 0 && s.sales().size() >= 3)
                count = Math.max(capacity, (int)Math.ceil(sold * 4 - s.stock())); }
            case "ZERO_SALES_PIVOT" -> { if (history.size() >= 2 && last.sold() == 0 &&
                history.get(history.size()-2).sold() == 0) {
                if (cycleNumber % 2 == 0) quality = quality.add(bd("0.15")); else assortment += 1;
            } }
            case "CONSERVATIVE_CASH_SCORE" -> { if (s.sales().size() >= 3) {
                BigDecimal best = null; int bestCount = count; BigDecimal bestPrice = price;
                for (int n : new int[]{capacity * 2, capacity * 3, capacity * 4})
                    for (BigDecimal factor : new BigDecimal[]{bd("0.9"), BigDecimal.ONE, bd("1.1")}) {
                        BigDecimal candidatePrice = price.multiply(factor);
                        if (candidatePrice.compareTo(cost.multiply(bd("1.05"))) <= 0) continue;
                        int duration = (n + capacity - 1) / capacity;
                        BigDecimal cash = balance.add(candidatePrice.multiply(BigDecimal.valueOf(
                            Math.min(s.stock()+n, (int)Math.floor(sold * duration * 0.7)))))
                            .subtract(cost.multiply(BigDecimal.valueOf(n))).subtract(upcoming(s, s.day()+duration));
                        if (best == null || cash.compareTo(best) > 0) { best = cash; bestCount = n; bestPrice = candidatePrice; }
                    }
                count = bestCount; price = bestPrice;
            } }
            case "LIMITED_CREDIT_GROWTH" -> { if (s.sales().size() >= 3 && zeros <= 1 && s.stock() < capacity &&
                upcoming(s, s.day()+6).compareTo(balance.max(BigDecimal.ZERO)) < 0) count += capacity; }
            case "ADS_DURATION_TEST" -> { ads = 2; adDays = cycleNumber % 2 == 0 ? 1 : 2;
                if (history.size() >= 2 && last.advertising() > 0 && history.get(history.size()-2).advertising() > 0) {
                    ObservedCycle previous = history.get(history.size()-2);
                    if (last.sold() / (double)last.days() > previous.sold() / (double)previous.days())
                        adDays = last.advertisingDays(); else adDays = previous.advertisingDays();
                } }
            case "QUALITY_PRICE_BUNDLE" -> { if (s.sales().size() >= 3 && zeros <= 1 &&
                balance.compareTo(cost.multiply(BigDecimal.valueOf(capacity*2L))) > 0 && s.stock() < capacity) {
                quality = quality.add(bd("0.08")); price = price.multiply(bd("1.10"));
            } else if (s.stock() >= capacity * 2 && last != null) {
                quality = last.quality(); price = last.price();
            } }
            default -> throw new IllegalArgumentException("Неизвестная политика бота: " + policy);
        }
        count = bounded(count, 1, capacity * 6);
        quality = quality.max(bd("0.20")).min(BigDecimal.ONE);
        assortment = bounded(assortment, 1, count);
        ads = bounded(ads, 0, 7);
        adDays = ads == 0 ? 0 : bounded(adDays == 0 ? 1 : adDays, 1, (count+capacity-1)/capacity);
        price = price.max(s.baseCost().multiply(quality).multiply(bd("1.05"))).max(bd("0.01"))
            .setScale(2, RoundingMode.HALF_UP);
        return new Decision(plan(count, price, quality, assortment, ads, adDays), false, false);
    }

    private static int affordable(StrategySnapshot s, BigDecimal quality, int ads, int days,
                                  BigDecimal balance, int capacity) {
        BigDecimal left = balance.subtract(s.baseAdvertising().multiply(BigDecimal.valueOf((long)ads*days)));
        BigDecimal cost = s.baseCost().multiply(quality);
        if (cost.signum() <= 0) return capacity * 3;
        return Math.max(1, Math.min(capacity * 6, left.max(BigDecimal.ZERO).divide(cost, 0, RoundingMode.DOWN).intValue()));
    }

    private static BigDecimal upcoming(StrategySnapshot s, int until) {
        return s.payments().stream().filter(p -> p.day() > s.day() && p.day() <= until)
            .map(StrategySnapshot.Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal median(List<BigDecimal> prices) {
        if (prices.isEmpty()) return null;
        var sorted = prices.stream().sorted().toList();
        return sorted.get(sorted.size()/2);
    }

    private static BigDecimal step(BigDecimal current, BigDecimal target, BigDecimal maxFraction) {
        return target.max(current.multiply(BigDecimal.ONE.subtract(maxFraction)))
            .min(current.multiply(BigDecimal.ONE.add(maxFraction)));
    }

    private static int bounded(int value, int low, int high) { return Math.max(low, Math.min(high, value)); }
    private static BigDecimal bd(String n) { return new BigDecimal(n); }

    private CommonProductionParameters plan(int count, BigDecimal price, BigDecimal quality, int assortment, int ads, int days) {
        var p = new CommonProductionParameters();
        p.setProductCount(count); p.setPrice(price); p.setQualityIndex(quality);
        p.setAssortment(assortment); p.setAdvertisingIntensityIndex(ads);
        p.setAdvertisingDays(ads == 0 ? 0 : Math.max(1, days));
        return p;
    }
}
