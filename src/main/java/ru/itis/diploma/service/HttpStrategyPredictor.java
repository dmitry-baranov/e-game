package ru.itis.diploma.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ru.itis.diploma.dto.StrategyAdvice;
import ru.itis.diploma.dto.StrategySnapshot;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public class HttpStrategyPredictor implements StrategyPredictor {
    private final String url;
    private final RestTemplate http;

    public HttpStrategyPredictor(@Value("${strategy.predictor.url:}") String url) {
        this.url = url;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(300);
        factory.setReadTimeout(1200);
        http = new RestTemplate(factory);
    }

    @Override
    public Optional<Predictions> predict(StrategySnapshot snapshot, List<StrategyAdvice.Card> cards) {
        if (url.isBlank() || cards.isEmpty() || !snapshot.opened()) return Optional.empty();
        List<Map<String, Object>> plans = cards.stream().map(c -> Map.<String, Object>of(
            "productCount", c.getProductCount(), "price", c.getPrice(), "quality", c.getQuality(),
            "assortment", c.getAssortment(), "advertisingIntensity", c.getAdvertisingIntensity(),
            "advertisingDays", c.getAdvertisingDays(), "cycleDays", c.getCycleDays())).toList();
        try {
            Predictions answer = http.postForObject(url + "/predict", Map.of("snapshot", snapshot, "plans", plans), Predictions.class);
            if (answer == null || answer.name() == null || answer.version() == null ||
                answer.estimates() == null || answer.estimates().size() != cards.size()) return Optional.empty();
            return Optional.of(answer);
        } catch (Exception e) {
            log.debug("Strategy predictor unavailable; using rules", e);
            return Optional.empty();
        }
    }
}
