package ru.itis.diploma.experiment.bot;

import org.springframework.stereotype.Component;
import ru.itis.diploma.experiment.bot.policy.PolicyContext;
import ru.itis.diploma.experiment.bot.policy.PolicyRegistry;
import ru.itis.diploma.game.dto.CommonProductionParameters;
import ru.itis.diploma.game.dto.StrategyAdvice;
import ru.itis.diploma.game.dto.StrategySnapshot;
import ru.itis.diploma.game.service.StrategyCoordinator;

import java.util.List;
import java.util.Map;

/** Public facade for the bot policy registry. */
@Component
public class BotPolicies {
    public static final String[] NAMES = PolicyRegistry.selectableNames();
    public static final Map<String, String> DESCRIPTIONS = PolicyRegistry.selectableDescriptions();

    private final PolicyRegistry registry;

    public BotPolicies(StrategyCoordinator coordinator) {
        registry = new PolicyRegistry(coordinator);
    }

    /** usedModel denotes the proposal source; application is counted only after accepting the plan. */
    public record Decision(CommonProductionParameters action, boolean usedModel, boolean fallback,
                           List<StrategyAdvice.Card> candidates, String fallbackReason) {
        public Decision(CommonProductionParameters action, boolean usedModel, boolean fallback) {
            this(action, usedModel, fallback, List.of(), null);
        }
    }

    public Decision decide(StrategySnapshot snapshot, String policy, long seed, int index,
                           String expectedModelVersion) {
        return decide(snapshot, policy, seed, index, expectedModelVersion, List.of());
    }

    public Decision decide(StrategySnapshot snapshot, String policy, long seed, int index,
                           String expectedModelVersion, List<ObservedCycle> history) {
        var decision = registry.decide(policy,
            new PolicyContext(snapshot, seed, index, expectedModelVersion, history));
        return new Decision(decision.action(), decision.usedModel(), decision.fallback(),
            decision.candidates(), decision.fallbackReason());
    }
}
