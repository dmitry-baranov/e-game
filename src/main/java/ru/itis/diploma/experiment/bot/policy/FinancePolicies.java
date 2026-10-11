package ru.itis.diploma.experiment.bot.policy;

final class FinancePolicies extends ThematicPolicy {
    @Override
    void apply(String policy, Plan plan) {
        switch (policy) {
            case "CASH_RESERVE" -> {
                if (plan.snapshot.opened()) plan.count = plan.affordable(plan.balance.multiply(bd("0.7")));
                plan.ads = 0;
            }
            case "DEBT_AVOID" -> {
                if (plan.snapshot.opened()) plan.count = plan.affordable(plan.balance);
                plan.ads = 0;
            }
            case "PAYMENT_AWARE" -> {
                if (plan.snapshot.opened()) {
                    var obligations = plan.upcoming(plan.snapshot.day() +
                        Math.max(1, (plan.count + plan.capacity - 1) / plan.capacity));
                    plan.count = plan.affordable(plan.balance.subtract(obligations));
                    plan.ads = 0;
                }
            }
            default -> throw unknown(policy);
        }
    }
}
