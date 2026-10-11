package ru.itis.diploma.experiment.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BotParticipantsTest {
    @Test
    void collectRotatesPoliciesBetweenAttempts() {
        String[] policies = {"CAUTIOUS", "PRICE", "QUALITY"};
        assertEquals("CAUTIOUS", BotParticipants.policyFor("COLLECT", policies, 1, 0));
        assertEquals("PRICE", BotParticipants.policyFor("COLLECT", policies, 1, 1));
        assertEquals("PRICE", BotParticipants.policyFor("COLLECT", policies, 2, 0));
        assertEquals("QUALITY", BotParticipants.policyFor("COLLECT", policies, 2, 1));
    }

    @Test
    void evaluationOnlyChangesFirstBotInPairedGames() {
        String[] policies = {"CAUTIOUS", "PRICE", "QUALITY"};
        assertEquals("EVAL_RULES", BotParticipants.policyFor("EVALUATE", policies, 1, 0));
        assertEquals("MODEL", BotParticipants.policyFor("EVALUATE", policies, 2, 0));
        for (int i = 1; i < 10; i++)
            assertEquals(BotParticipants.policyFor("EVALUATE", policies, 1, i),
                BotParticipants.policyFor("EVALUATE", policies, 2, i));
    }
}
