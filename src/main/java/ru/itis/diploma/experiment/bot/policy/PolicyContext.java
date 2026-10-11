package ru.itis.diploma.experiment.bot.policy;

import ru.itis.diploma.experiment.bot.ObservedCycle;
import ru.itis.diploma.game.dto.StrategySnapshot;

import java.util.List;
import java.util.SplittableRandom;

public record PolicyContext(StrategySnapshot snapshot, long seed, int index, String expectedModelVersion,
                            List<ObservedCycle> history) {
    public PolicyContext {
        history = List.copyOf(history);
    }

    public SplittableRandom random() {
        return new SplittableRandom(seed ^ (index * 0x9e3779b97f4a7c15L) ^ ((long) snapshot.day() << 32));
    }
}
