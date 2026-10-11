package ru.itis.diploma.bot;

import org.junit.jupiter.api.Test;
import ru.itis.diploma.experiment.ExperimentConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BotParticipantsTest {
    @Test
    void collectRotatesPoliciesBetweenAttempts() {
        var config = new ExperimentConfig();
        config.setPolicyMix("CAUTIOUS,PRICE,QUALITY");
        assertEquals("CAUTIOUS", BotParticipants.policyFor(config, 1, 0));
        assertEquals("PRICE", BotParticipants.policyFor(config, 1, 1));
        assertEquals("PRICE", BotParticipants.policyFor(config, 2, 0));
        assertEquals("QUALITY", BotParticipants.policyFor(config, 2, 1));
    }

    @Test
    void evaluationOnlyChangesFirstBotInPairedGames() {
        var config = new ExperimentConfig();
        config.setMode("EVALUATE");
        config.setPolicyMix("CAUTIOUS,PRICE,QUALITY");
        assertEquals("EVAL_RULES", BotParticipants.policyFor(config, 1, 0));
        assertEquals("MODEL", BotParticipants.policyFor(config, 2, 0));
        for (int i = 1; i < 10; i++)
            assertEquals(BotParticipants.policyFor(config, 1, i), BotParticipants.policyFor(config, 2, i));
    }
}
