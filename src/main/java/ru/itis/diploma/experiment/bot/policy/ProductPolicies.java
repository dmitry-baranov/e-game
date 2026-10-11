package ru.itis.diploma.experiment.bot.policy;

import java.math.BigDecimal;

final class ProductPolicies extends ThematicPolicy {
    @Override
    void apply(String policy, Plan plan) {
        switch (policy) {
            case "QUALITY_STEP_UP" -> {
                if (plan.snapshot.sales().size() >= 3 && plan.zeros <= 1 &&
                    plan.balance.compareTo(plan.cost.multiply(BigDecimal.valueOf(plan.capacity * 2L))) > 0)
                    plan.quality = plan.quality.add(bd("0.08"));
            }
            case "QUALITY_BUDGET" -> {
                if (plan.snapshot.opened() &&
                    plan.balance.compareTo(plan.cost.multiply(BigDecimal.valueOf(plan.count))) < 0)
                    plan.quality = plan.quality.subtract(bd("0.12"));
            }
            case "ASSORTMENT_BROADEN" -> {
                if (plan.sellouts >= 2 && plan.balance.signum() > 0) plan.assortment += 1;
            }
            case "ASSORTMENT_FOCUS" -> {
                if (plan.zeros >= 2 || (plan.snapshot.opened() && plan.balance.signum() <= 0)) plan.assortment -= 1;
            }
            default -> throw unknown(policy);
        }
    }
}
