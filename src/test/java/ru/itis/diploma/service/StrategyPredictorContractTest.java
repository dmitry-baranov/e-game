package ru.itis.diploma.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StrategyPredictorContractTest {
    @Test
    void readsModelResponseIncludingAbstentionForOutOfRangeAction() throws Exception {
        var mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        var result = mapper.readValue("{\"source\":\"MODEL\",\"name\":\"CatBoost\",\"version\":\"v1\"," +
            "\"predictions\":[{\"low\":2,\"typical\":4,\"high\":8},null]}", StrategyPredictor.Predictions.class);
        assertEquals("CatBoost", result.name());
        assertEquals(4, result.estimates().get(0).typical());
        assertNull(result.estimates().get(1));
    }
}
