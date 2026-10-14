package ru.itis.diploma.experiment.bot.policy;

import ru.itis.diploma.experiment.bot.ObservedCycle;
import ru.itis.diploma.game.dto.StrategySnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

abstract class ThematicPolicy {
    final PolicyDecision decide(PolicyContext context, String policy) {
        var plan = new Plan(context);
        apply(policy, plan);
        return plan.decision();
    }

    abstract void apply(String policy, Plan plan);

    static final class Plan {
        final StrategySnapshot snapshot;
        final List<ObservedCycle> history;
        final int capacity;
        final BigDecimal balance;
        final BigDecimal cost;
        final BigDecimal ownPrice;
        final BigDecimal ownQuality;
        final int ownAds;
        final double sold;
        final long zeros;
        final long sellouts;
        final BigDecimal median;
        final ObservedCycle last;
        final int cycleNumber;
        int count;
        BigDecimal quality;
        int assortment;
        BigDecimal price;
        int ads;
        int adDays;

        Plan(PolicyContext context) {
            snapshot = context.snapshot();
            history = context.history();
            capacity = Math.max(1, snapshot.capacity() == null ? 10 + context.index() % 5 : snapshot.capacity());
            balance = snapshot.balance() == null ? BigDecimal.ZERO : snapshot.balance();
            count = snapshot.productCount() == null ? capacity * 3 : snapshot.productCount();
            quality = snapshot.quality() == null ? bd("0.60") : snapshot.quality();
            assortment = snapshot.assortment() == null ? 2 : snapshot.assortment();
            cost = snapshot.baseCost().multiply(quality);
            price = snapshot.price() == null ? cost.multiply(bd("1.8")) : snapshot.price();
            ads = snapshot.advertisingIntensity() == null ? 0 : snapshot.advertisingIntensity();
            adDays = 0;
            ownPrice = price;
            ownQuality = quality;
            ownAds = ads;
            sold = snapshot.sales().stream().mapToInt(StrategySnapshot.Sale::sold).average().orElse(0);
            zeros = snapshot.sales().stream().filter(day -> day.sold() == 0).count();
            sellouts = snapshot.sales().stream().filter(day -> day.sold() > 0 && day.stock() == 0).count();
            median = median(snapshot.competitors().stream().map(StrategySnapshot.Competitor::price).toList());
            last = history.isEmpty() ? null : history.get(history.size() - 1);
            cycleNumber = history.size();
        }

        PolicyDecision decision() {
            return new PolicyDecision(PlanNormalizer.normalize(snapshot, PlanNormalizer.Profile.EXPANDED,
                count, price, quality, assortment, ads, adDays, capacity));
        }

        int affordable(BigDecimal availableBalance) {
            BigDecimal left = availableBalance;
            BigDecimal unitCost = snapshot.baseCost().multiply(quality);
            if (unitCost.signum() <= 0) return capacity * 3;
            return Math.max(1, Math.min(capacity * 6,
                left.max(BigDecimal.ZERO).divide(unitCost, 0, RoundingMode.DOWN).intValue()));
        }

        BigDecimal upcoming(int until) {
            return snapshot.payments().stream().filter(payment -> payment.day() > snapshot.day() && payment.day() <= until)
                .map(StrategySnapshot.Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    static IllegalArgumentException unknown(String policy) {
        return new IllegalArgumentException("Неизвестная политика бота: " + policy);
    }

    static BigDecimal step(BigDecimal current, BigDecimal target, BigDecimal maxFraction) {
        return target.max(current.multiply(BigDecimal.ONE.subtract(maxFraction)))
            .min(current.multiply(BigDecimal.ONE.add(maxFraction)));
    }

    static int bounded(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }

    static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private static BigDecimal median(List<BigDecimal> prices) {
        if (prices.isEmpty()) return null;
        var sorted = prices.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }
}
