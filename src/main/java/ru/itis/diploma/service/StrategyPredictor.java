package ru.itis.diploma.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import ru.itis.diploma.dto.StrategyAdvice;
import ru.itis.diploma.dto.StrategySnapshot;
import java.util.List;
import java.util.Optional;

public interface StrategyPredictor {
    record Estimate(int low, int typical, int high) {}
    record Comparison(String name, String version, @JsonProperty("predictions") List<Estimate> estimates) {}
    record Predictions(String name, String version, @JsonProperty("predictions") List<Estimate> estimates,
                       List<Comparison> comparisons, List<String> reasons) {
        public Predictions(String name, String version, List<Estimate> estimates) {
            this(name, version, estimates, List.of(), List.of());
        }
        public Predictions(String name, String version, List<Estimate> estimates,
                           List<Comparison> comparisons) {
            this(name, version, estimates, comparisons, List.of());
        }
    }

    Optional<Predictions> predict(StrategySnapshot snapshot, List<StrategyAdvice.Card> cards);
}
