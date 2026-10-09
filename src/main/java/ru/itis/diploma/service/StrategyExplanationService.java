package ru.itis.diploma.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import ru.itis.diploma.dto.StrategyAdvice;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class StrategyExplanationService {
    private final String url;
    private final String key;
    private final String model;
    private final String provider;
    private final RestTemplate http;
    private final ObjectMapper mapper = new ObjectMapper();

    public StrategyExplanationService(@Value("${strategy.llm.url:}") String url,
                                       @Value("${strategy.llm.key:}") String key,
                                       @Value("${strategy.llm.model:}") String model,
                                       @Value("${strategy.llm.provider:openai}") String provider) {
        this.url = url;
        this.key = key;
        this.model = model;
        this.provider = provider;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(400);
        factory.setReadTimeout(1800);
        http = new RestTemplate(factory);
    }

    /** LLM sees only validated card descriptions; it cannot add numbers or change recommendations. */
    public String explain(StrategyAdvice advice) {
        if ("disabled".equals(provider) || url.isBlank() || model.isBlank() ||
            ("openai".equals(provider) && key.isBlank()) ||
            !List.of("openai", "ollama", "llama_cpp").contains(provider) ||
            advice.getRecommendations().isEmpty()) return null;
        String facts = advice.getRecommendations().stream().map(c ->
            c.getName() + ": " + c.getReason() + " Риск: " + c.getRiskLevel() +
                ". Источник: " + c.getSource()).reduce((a, b) -> a + "\n" + b).orElse("");
        if (advice.getModelOpinions() != null) for (var opinion : advice.getModelOpinions())
            facts += "\nМодель " + opinion.getModel() + " выбрала " + opinion.getAction() +
                ": " + opinion.getRationale();
        if (facts.length() > 3000) return null;
        try {
            HttpHeaders headers = new HttpHeaders();
            if (!key.isBlank()) headers.setBearerAuth(key);
            headers.setContentType(MediaType.APPLICATION_JSON);
            var body = Map.of("model", model, "temperature", 0, "max_tokens", 160,
                "messages", List.of(
                    Map.of("role", "system", "content", "Объясни проверенные игровые альтернативы на русском в двух предложениях. " +
                        "Не придумывай факты, числа, вероятности или новые действия. Не используй цифры."),
                    Map.of("role", "user", "content", "Проверенные карточки:\n" + facts)));
            String responseBody = http.postForObject(url, new HttpEntity<>(body, headers), String.class);
            if (responseBody == null) return null;
            JsonNode response = mapper.readTree(responseBody);
            JsonNode content = response.path("choices").path(0).path("message").path("content");
            if (!content.isTextual()) return null;
            String text = content.asText().trim();
            if (text.length() > 500 || text.length() < 10 || text.matches("(?s).*\\d.*")) return null;
            return text;
        } catch (Exception ex) {
            log.debug("LLM explanation unavailable", ex);
            return null;
        }
    }
}
