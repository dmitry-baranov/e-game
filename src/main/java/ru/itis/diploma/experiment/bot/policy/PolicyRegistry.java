package ru.itis.diploma.experiment.bot.policy;

import ru.itis.diploma.game.service.StrategyCoordinator;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PolicyRegistry {
    private static final PolicyCatalog[] SELECTABLE = java.util.Arrays.stream(PolicyCatalog.values())
        .filter(policy -> policy.metadata().selectable()).toArray(PolicyCatalog[]::new);
    private final Map<String, BotPolicy> policies;

    public PolicyRegistry(StrategyCoordinator coordinator) {
        var basic = new BasicPolicies();
        var recommendations = new RecommendationPolicyAdapter(coordinator);
        Map<PolicyMetadata.Family, ThematicPolicy> thematic = new EnumMap<>(PolicyMetadata.Family.class);
        thematic.put(PolicyMetadata.Family.PRICE, new PricePolicies());
        thematic.put(PolicyMetadata.Family.INVENTORY, new InventoryPolicies());
        thematic.put(PolicyMetadata.Family.PRODUCT, new ProductPolicies());
        thematic.put(PolicyMetadata.Family.ADVERTISING, new AdvertisingPolicies());
        thematic.put(PolicyMetadata.Family.FINANCE, new FinancePolicies());
        thematic.put(PolicyMetadata.Family.MARKET, new MarketPolicies());
        thematic.put(PolicyMetadata.Family.EXPERIMENT, new ExperimentPolicies());
        Map<String, BotPolicy> registered = new LinkedHashMap<>();
        for (PolicyCatalog policy : PolicyCatalog.values()) {
            BotPolicy handler = switch (policy.metadata().family()) {
                case BASIC -> context -> basic.decide(context, policy.name());
                case RECOMMENDATION -> context -> {
                    PolicyDecision decision = recommendations.decide(context, policy.name());
                    return decision == null ? basic.decide(context, policy.name()) : decision;
                };
                default -> context -> thematic.get(policy.metadata().family()).decide(context, policy.name());
            };
            registered.put(policy.name(), handler);
        }
        policies = Map.copyOf(registered);
    }

    public PolicyDecision decide(String name, PolicyContext context) {
        BotPolicy policy = policies.get(name);
        if (policy == null) throw new IllegalArgumentException("Неизвестная политика бота: " + name);
        return policy.decide(context);
    }

    public static String[] selectableNames() {
        return java.util.Arrays.stream(SELECTABLE).map(Enum::name).toArray(String[]::new);
    }

    public static Map<String, String> selectableDescriptions() {
        Map<String, String> result = new LinkedHashMap<>();
        for (PolicyCatalog policy : SELECTABLE)
            result.put(policy.name(), policy.metadata().description());
        return Collections.unmodifiableMap(result);
    }
}
