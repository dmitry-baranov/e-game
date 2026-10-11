package ru.itis.diploma.experiment.bot.policy;

final class MarketPolicies extends ThematicPolicy {
    @Override
    void apply(String policy, Plan plan) {
        switch (policy) {
            case "RIVAL_QUALITY_GAP" -> {
                if (plan.snapshot.competitors().stream().anyMatch(competitor -> competitor.quality() != null &&
                    competitor.price() != null && competitor.price().subtract(plan.ownPrice).abs()
                        .compareTo(plan.ownPrice.multiply(bd("0.25"))) < 0 &&
                    competitor.quality().subtract(plan.ownQuality).compareTo(bd("0.15")) > 0) &&
                    plan.balance.signum() > 0)
                    plan.quality = plan.quality.add(bd("0.12"));
            }
            case "RIVAL_AD_GAP" -> {
                if (plan.snapshot.competitors().stream().anyMatch(competitor ->
                    competitor.advertisingIntensity() != null &&
                        competitor.advertisingIntensity() >= plan.ownAds + 2) &&
                    plan.balance.compareTo(plan.snapshot.baseAdvertising().multiply(bd("3"))) > 0)
                    plan.ads = Math.min(4, plan.ads + 1);
                plan.adDays = plan.ads == 0 ? 0 : 1;
            }
            default -> throw unknown(policy);
        }
    }
}
