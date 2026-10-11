package ru.itis.diploma.experiment.bot.policy;

import ru.itis.diploma.game.dto.CommonProductionParameters;
import ru.itis.diploma.game.dto.StrategyAdvice;

import java.util.List;

public record PolicyDecision(CommonProductionParameters action, boolean usedModel, boolean fallback,
                             List<StrategyAdvice.Card> candidates, String fallbackReason) {
    public PolicyDecision(CommonProductionParameters action) {
        this(action, false, false, List.of(), null);
    }
}
