package ru.itis.diploma.experiment.bot.policy;

final class AdvertisingPolicies extends ThematicPolicy {
    @Override
    void apply(String policy, Plan plan) {
        switch (policy) {
            case "ADS_PULSE" -> {
                plan.ads = plan.cycleNumber % 2 == 0 && (!plan.snapshot.opened() || plan.balance.signum() > 0) ? 2 : 0;
                plan.adDays = plan.ads == 0 ? 0 : 1;
            }
            case "ADS_STOP_LOSS" -> {
                plan.ads = plan.last != null && plan.last.advertising() > 0 &&
                    plan.last.sold() < plan.last.days() * 2 ? 0 : 2;
                plan.adDays = plan.ads == 0 ? 0 : 1;
            }
            default -> throw unknown(policy);
        }
    }
}
