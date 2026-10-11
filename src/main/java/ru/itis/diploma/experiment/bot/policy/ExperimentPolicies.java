package ru.itis.diploma.experiment.bot.policy;

import ru.itis.diploma.experiment.bot.ObservedCycle;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class ExperimentPolicies extends ThematicPolicy {
    @Override
    void apply(String policy, Plan plan) {
        switch (policy) {
            case "PRICE_TWO_LEVELS" ->
                plan.price = plan.cost.multiply(plan.cycleNumber % 2 == 0 ? bd("1.45") : bd("1.90"));
            case "PRICE_REVENUE_WINDOW" -> applyRevenueWindow(plan);
            case "VOLATILITY_BUFFER" -> {
                if (plan.snapshot.sales().size() >= 3) {
                    double variance = plan.snapshot.sales().stream()
                        .mapToDouble(day -> Math.pow(day.sold() - plan.sold, 2)).average().orElse(0);
                    plan.count = variance > Math.pow(Math.max(1, plan.sold), 2) / 4 ?
                        plan.capacity * 2 : plan.capacity * 3;
                }
            }
            case "STOCK_RUNWAY" -> {
                if (plan.sold > 0 && plan.snapshot.sales().size() >= 3)
                    plan.count = Math.max(plan.capacity,
                        (int) Math.ceil(plan.sold * 4 - plan.snapshot.stock()));
            }
            case "ZERO_SALES_PIVOT" -> {
                if (plan.history.size() >= 2 && plan.last.sold() == 0 &&
                    plan.history.get(plan.history.size() - 2).sold() == 0) {
                    if (plan.cycleNumber % 2 == 0) plan.quality = plan.quality.add(bd("0.15"));
                    else plan.assortment += 1;
                }
            }
            case "CONSERVATIVE_CASH_SCORE" -> applyConservativeCashScore(plan);
            case "LIMITED_CREDIT_GROWTH" -> {
                if (plan.snapshot.sales().size() >= 3 && plan.zeros <= 1 &&
                    plan.snapshot.stock() < plan.capacity &&
                    plan.upcoming(plan.snapshot.day() + 6).compareTo(plan.balance.max(BigDecimal.ZERO)) < 0)
                    plan.count += plan.capacity;
            }
            case "ADS_DURATION_TEST" -> applyAdsDurationTest(plan);
            case "QUALITY_PRICE_BUNDLE" -> applyQualityPriceBundle(plan);
            default -> throw unknown(policy);
        }
    }

    private static void applyRevenueWindow(Plan plan) {
        if (plan.history.size() < 2) return;
        ObservedCycle previous = plan.history.get(plan.history.size() - 2);
        if (Math.abs(previous.count() - plan.last.count()) <= Math.max(plan.capacity, plan.last.count() / 5) &&
            previous.price().compareTo(plan.last.price()) != 0) {
            BigDecimal first = previous.revenue().divide(BigDecimal.valueOf(previous.days()), 2, RoundingMode.HALF_UP);
            BigDecimal second = plan.last.revenue().divide(BigDecimal.valueOf(plan.last.days()), 2, RoundingMode.HALF_UP);
            plan.price = step(plan.price, first.compareTo(second) > 0 ? previous.price() : plan.last.price(), bd("0.10"));
        }
    }

    private static void applyConservativeCashScore(Plan plan) {
        if (plan.snapshot.sales().size() < 3) return;
        BigDecimal best = null;
        int bestCount = plan.count;
        BigDecimal bestPrice = plan.price;
        for (int count : new int[]{plan.capacity * 2, plan.capacity * 3, plan.capacity * 4}) {
            for (BigDecimal factor : new BigDecimal[]{bd("0.9"), BigDecimal.ONE, bd("1.1")}) {
                BigDecimal candidatePrice = plan.price.multiply(factor);
                if (candidatePrice.compareTo(plan.cost.multiply(bd("1.05"))) <= 0) continue;
                int duration = (count + plan.capacity - 1) / plan.capacity;
                BigDecimal cash = plan.balance.add(candidatePrice.multiply(BigDecimal.valueOf(
                        Math.min(plan.snapshot.stock() + count, (int) Math.floor(plan.sold * duration * 0.7)))))
                    .subtract(plan.cost.multiply(BigDecimal.valueOf(count)))
                    .subtract(plan.upcoming(plan.snapshot.day() + duration));
                if (best == null || cash.compareTo(best) > 0) {
                    best = cash;
                    bestCount = count;
                    bestPrice = candidatePrice;
                }
            }
        }
        plan.count = bestCount;
        plan.price = bestPrice;
    }

    private static void applyAdsDurationTest(Plan plan) {
        plan.ads = 2;
        plan.adDays = plan.cycleNumber % 2 == 0 ? 1 : 2;
        if (plan.history.size() >= 2 && plan.last.advertising() > 0 &&
            plan.history.get(plan.history.size() - 2).advertising() > 0) {
            ObservedCycle previous = plan.history.get(plan.history.size() - 2);
            if (plan.last.sold() / (double) plan.last.days() > previous.sold() / (double) previous.days())
                plan.adDays = plan.last.advertisingDays();
            else plan.adDays = previous.advertisingDays();
        }
    }

    private static void applyQualityPriceBundle(Plan plan) {
        if (plan.snapshot.sales().size() >= 3 && plan.zeros <= 1 &&
            plan.balance.compareTo(plan.cost.multiply(BigDecimal.valueOf(plan.capacity * 2L))) > 0 &&
            plan.snapshot.stock() < plan.capacity) {
            plan.quality = plan.quality.add(bd("0.08"));
            plan.price = plan.price.multiply(bd("1.10"));
        } else if (plan.snapshot.stock() >= plan.capacity * 2 && plan.last != null) {
            plan.quality = plan.last.quality();
            plan.price = plan.last.price();
        }
    }
}
