package ru.itis.diploma.experiment.bot.policy;

import ru.itis.diploma.game.service.StrategyCoordinator;

import java.util.List;

final class RecommendationPolicyAdapter {
    private final StrategyCoordinator coordinator;

    RecommendationPolicyAdapter(StrategyCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    PolicyDecision decide(PolicyContext context, String policy) {
        boolean modelPolicy = switch (policy) {
            case "RULES", "EVAL_RULES" -> false;
            case "MODEL" -> true;
            default -> throw new IllegalArgumentException("Неизвестная политика бота: " + policy);
        };
        var s = context.snapshot();
        var advice = coordinator.recommend(s, null);
        var cards = advice.getRecommendations();
        if (cards.isEmpty()) return null;
        var initialKey = "RULES".equals(policy) ? "CAUTIOUS" : "QUALITY";
        var card = modelPolicy && s.opened() ? cards.get(0) :
            cards.stream().filter(c -> (s.opened() ? "HOLD" : initialKey).equals(c.getKey()))
                .findFirst().orElse(cards.get(0));
        boolean model = modelPolicy && s.opened() && cards.size() > 1 &&
            cards.stream().allMatch(c -> "MODEL".equals(c.getSource()) && c.getLowCash() != null);
        if (model && !context.expectedModelVersion().equals(card.getModelVersion()))
            throw new IllegalStateException("Версия модели изменилась во время оценки");
        if (modelPolicy && !model)
            card = cards.stream().filter(c -> (s.opened() ? "HOLD" : initialKey).equals(c.getKey()))
                .findFirst().orElse(cards.get(0));
        var action = PlanNormalizer.normalize(s, PlanNormalizer.Profile.RECOMMENDATION,
            card.getProductCount(), card.getPrice(), card.getQuality(), card.getAssortment(),
            card.getAdvertisingIntensity(), card.getAdvertisingDays());
        boolean fallback = modelPolicy && !model;
        String reason = fallback ? (s.opened() ?
            (advice.getModelFallbackReason() == null ? "NO_COMPARABLE_CARDS" : advice.getModelFallbackReason()) :
            "STARTING_GAME") : null;
        return new PolicyDecision(action, model, fallback, List.copyOf(cards), reason);
    }
}
