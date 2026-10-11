package ru.itis.diploma.experiment.bot.policy;

@FunctionalInterface
public interface BotPolicy {
    PolicyDecision decide(PolicyContext context);
}
