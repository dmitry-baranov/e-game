package ru.itis.diploma.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import ru.itis.diploma.dto.StrategyAdvice;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class StrategyExplanationServiceTest {
    private StrategyAdvice advice() {
        var card = new StrategyAdvice.Card();
        card.setName("Сохранить выпуск");
        card.setReason("Спрос пока неизвестен");
        card.setRiskLevel("умеренный");
        var advice = new StrategyAdvice();
        advice.setRecommendations(List.of(card));
        return advice;
    }

    @Test
    void localQwenWorksWithoutBearerKeyButCannotIntroduceNumbers() throws Exception {
        var request = new AtomicReference<String>();
        var header = new AtomicReference<String>();
        var result = new AtomicReference<>("Пока стоит сохранить план и наблюдать продажи.");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            header.set(exchange.getRequestHeaders().getFirst("Authorization"));
            request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var bytes = ("{\"choices\":[{\"message\":{\"content\":\"" + result.get() + "\"}}]}")
                .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        server.start();
        try {
            var url = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/chat/completions";
            var local = new StrategyExplanationService(url, "", "qwen", "ollama");
            assertEquals(result.get(), local.explain(advice()));
            assertNull(header.get());
            assertTrue(request.get().contains("Спрос пока неизвестен"));
            result.set("Следует повысить цену на 10 процентов.");
            assertNull(local.explain(advice()));
            assertNull(new StrategyExplanationService(url, "", "qwen", "openai").explain(advice()));
            assertNull(new StrategyExplanationService(url, "token", "qwen", "disabled").explain(advice()));
        } finally {
            server.stop(0);
        }
    }
}
