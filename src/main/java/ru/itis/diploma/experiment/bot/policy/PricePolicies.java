package ru.itis.diploma.experiment.bot.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class PricePolicies extends ThematicPolicy {
    @Override
    void apply(String policy, Plan plan) {
        switch (policy) {
            case "PRICE_MEDIAN" -> {
                if (plan.median != null) plan.price = step(plan.price, plan.median, bd("0.12"));
            }
            case "PRICE_PREMIUM" -> {
                if (plan.median != null)
                    plan.price = step(plan.price, plan.median.multiply(bd("1.15")), bd("0.15"));
            }
            case "PRICE_CUT_AFTER_ZERO" -> {
                if (plan.snapshot.sales().size() >= 3 && plan.zeros >= 3)
                    plan.price = plan.price.multiply(bd("0.90"));
            }
            case "PRICE_RAISE_AFTER_SELLOUT" -> {
                if (plan.sellouts >= 2) plan.price = plan.price.multiply(bd("1.08"));
            }
            case "PRICE_MARGIN_GUARD" -> plan.price = plan.cost.multiply(bd("1.35"))
                .divide(BigDecimal.ONE.subtract(plan.snapshot.taxRate().divide(bd("100"), 6, RoundingMode.HALF_UP)),
                    2, RoundingMode.HALF_UP);
            case "PRICE_HYSTERESIS" -> {
                if (plan.median != null && plan.history.size() >= 2 &&
                    plan.history.get(plan.history.size() - 2).price().subtract(plan.price).abs()
                        .compareTo(plan.price.multiply(bd("0.02"))) <= 0 &&
                    plan.price.subtract(plan.median).abs().compareTo(plan.median.multiply(bd("0.15"))) > 0)
                    plan.price = step(plan.price, plan.median, bd("0.10"));
            }
            default -> throw unknown(policy);
        }
    }
}
