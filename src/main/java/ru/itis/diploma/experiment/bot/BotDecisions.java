package ru.itis.diploma.experiment.bot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.itis.diploma.experiment.bot.model.BotDecision;
import ru.itis.diploma.game.dto.StrategySnapshot;
import ru.itis.diploma.game.model.Manufacturer;

import java.math.BigDecimal;
import java.util.Set;

/**
 * Граница между движком игры и политикой: бот видит снимок ДО применения решений этого дня,
 * а не скрытые настройки покупателя или планы других ботов. Запись результатов
 * в серию и журнал находится у вызывающего модуля экспериментов.
 */
@Service
@RequiredArgsConstructor
public class BotDecisions {
    private static final Set<String> RETRYABLE_OUTCOMES = Set.of("STOCK_SKIP", "CREDIT_SKIP");

    private final BotPolicies policies;
    private final ObjectMapper mapper;

    /**
     * Вычисляет решение по снимку, полученному до хода, и не раскрывает политике
     * состояние других участников сверх данных снимка.
     *
     * @param player предприятие, принимающее решение
     * @param view доступный боту снимок состояния
     * @param index устойчивый индекс бота в игре
     * @return решение политики
     */
    public BotDecision decide(Manufacturer player, StrategySnapshot view, int index) {
        String policy = player.getSelectedStrategy();
        return policies.decide(view, policy, index);
    }

    /**
     * После STOCK_SKIP/CREDIT_SKIP не пересчитываем заведомо тот же недопустимый план
     * минимум три дня; позже ждём уменьшения склада или улучшения баланса. В дни
     * без новой попытки отсутствует запись решения — это не потерянная строка журнала.
     *
     * @param outcome результат предыдущей попытки применения плана
     * @param previousDay день предыдущей попытки
     * @param previousSnapshot сериализованный снимок перед предыдущей попыткой
     * @param view текущий снимок состояния
     * @return {@code true}, если повторное вычисление следует отложить
     */
    public boolean unchangedAfterSkip(String outcome, int previousDay, String previousSnapshot,
                                      StrategySnapshot view) {
        if (!RETRYABLE_OUTCOMES.contains(outcome)) {
            return false;
        }
        int nextAllowedDay = previousDay + 3;
        if (view.day() < nextAllowedDay) {
            return true;
        }

        try {
            JsonNode before = mapper.readTree(previousSnapshot);
            if ("STOCK_SKIP".equals(outcome)) {
                JsonNode stock = before.path("stock");
                int previousStock = stock.asInt();
                return view.stock() >= previousStock;
            }

            JsonNode balance = before.path("balance");
            BigDecimal previousBalance = new BigDecimal(balance.asText());
            return view.balance().compareTo(previousBalance) <= 0;
        } catch (JsonProcessingException | NumberFormatException e) {
            return false;
        }
    }

}
