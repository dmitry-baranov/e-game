package ru.itis.diploma.experiment.bot.policy;

final class InventoryPolicies extends ThematicPolicy {
    @Override
    void apply(String policy, Plan plan) {
        switch (policy) {
            case "SALES_SMOOTH" -> {
                if (plan.snapshot.sales().size() >= 3)
                    plan.count = bounded((int) Math.round((plan.sold * 3 + plan.count / 2.0) / 2),
                        plan.capacity, plan.capacity * 6);
            }
            case "STOCK_CLEAR" -> {
                if (plan.snapshot.stock() > plan.count / 2) {
                    plan.count = Math.max(1, plan.count - plan.capacity);
                    plan.price = plan.price.multiply(bd("0.95"));
                }
            }
            case "STOCKOUT_RECOVER" -> {
                if (plan.sellouts >= 2) plan.count += plan.capacity;
            }
            case "CAPACITY_STEADY" ->
                plan.count = plan.snapshot.stock() > plan.capacity * 3 ? plan.capacity * 2 : plan.capacity * 3;
            case "SHORT_CYCLE" -> plan.count = plan.capacity;
            case "LONG_CYCLE" -> plan.count = plan.snapshot.sales().size() >= 3 && plan.zeros == 0 &&
                plan.snapshot.stock() <= plan.capacity ? plan.capacity * 5 : plan.capacity * 2;
            default -> throw unknown(policy);
        }
    }
}
