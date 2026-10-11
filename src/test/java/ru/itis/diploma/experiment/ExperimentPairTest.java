package ru.itis.diploma.experiment;

import org.junit.jupiter.api.Test;
import ru.itis.diploma.experiment.model.ExperimentPair;
import ru.itis.diploma.experiment.model.ExperimentRun;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExperimentPairTest {
    @Test
    void onlyTwoValidHalvesCompletePair() {
        var pair = new ExperimentPair();
        var rules = run(pair);
        var model = run(pair);

        pair.finish(rules, List.of(rules, model), true);
        assertEquals("WAITING_PAIR", rules.getStatus());
        assertEquals("PENDING", pair.getStatus());

        pair.finish(model, List.of(rules, model), true);
        assertEquals("COMPLETED", rules.getStatus());
        assertEquals("COMPLETED", model.getStatus());
        assertEquals("COMPLETED", pair.getStatus());
    }

    @Test
    void invalidHalfInvalidatesWaitingHalfAndPair() {
        var pair = new ExperimentPair();
        var rules = run(pair);
        var model = run(pair);

        pair.finish(rules, List.of(rules, model), true);
        pair.finish(model, List.of(rules, model), false);

        assertEquals("INCOMPLETE", rules.getStatus());
        assertEquals("INCOMPLETE", model.getStatus());
        assertEquals("INCOMPLETE", pair.getStatus());
    }

    @Test
    void successfulHalfDoesNotOverrideFailedPair() {
        var pair = new ExperimentPair();
        var rules = run(pair);
        var model = run(pair);

        pair.finish(rules, List.of(rules, model), false);
        pair.finish(model, List.of(rules, model), true);

        assertEquals("INCOMPLETE", rules.getStatus());
        assertEquals("INCOMPLETE", model.getStatus());
        assertEquals("INCOMPLETE", pair.getStatus());
    }

    private ExperimentRun run(ExperimentPair pair) {
        var run = new ExperimentRun();
        run.setPair(pair);
        return run;
    }
}
