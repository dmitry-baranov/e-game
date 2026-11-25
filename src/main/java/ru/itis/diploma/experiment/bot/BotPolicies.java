package ru.itis.diploma.experiment.bot;

import org.springframework.stereotype.Component;
import ru.itis.diploma.experiment.bot.model.BotDecision;
import ru.itis.diploma.game.dto.CommonProductionParameters;
import ru.itis.diploma.game.dto.StrategySnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Выбирает одну из трёх политик и рассчитывает предлагаемый ботом план производства. */
@Component
public class BotPolicies {
    public static final String[] NAMES = {"CAUTIOUS", "PRICE", "STOCK"};
    public static final Map<String, String> DESCRIPTIONS;

    static {
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("CAUTIOUS", "Осторожный выпуск");
        descriptions.put("PRICE", "Цена дешевле видимого конкурента");
        descriptions.put("STOCK", "Выпуск по размеру склада");
        DESCRIPTIONS = Collections.unmodifiableMap(descriptions);
    }

    /**
     * Вычисляет решение выбранной политики по доступному снимку игры.
     *
     * @param snapshot доступный политике снимок игры
     * @param policy имя политики
     * @param index индекс бота
     * @return нормализованное решение
     * @throws IllegalArgumentException если политика не зарегистрирована
     */
    public BotDecision decide(StrategySnapshot snapshot, String policy, int index) {
        // На старте часть полей снимка ещё не заполнена: берём стартовые значения.
        int capacity = 10 + index % 5;
        if (snapshot.capacity() != null) {
            capacity = snapshot.capacity();
        }

        int count = capacity * 3;
        if (snapshot.productCount() != null) {
            count = snapshot.productCount();
        }

        BigDecimal quality = new BigDecimal("0.6");
        if (snapshot.quality() != null) {
            quality = snapshot.quality();
        }

        int assortment = 2;
        if (snapshot.assortment() != null) {
            assortment = snapshot.assortment();
        }

        // Каждая политика меняет только нужные ей параметры.
        switch (policy) {
            case "CAUTIOUS" -> {
                count = capacity * 2;
                quality = new BigDecimal("0.5");
            }
            case "PRICE" -> count = capacity * 3;
            case "STOCK" -> {
                int change = capacity;
                if (snapshot.stock() > count) {
                    change = -capacity;
                }
                int proposedCount = count + change;
                int maximumCount = capacity * 5;
                count = Math.min(proposedCount, maximumCount);
                count = Math.max(count, capacity);
            }
            default -> throw new IllegalArgumentException("Неизвестная политика бота: " + policy);
        }

        // Базовая цена учитывает качество; PRICE может снизить её относительно конкурента.
        BigDecimal cost = snapshot.baseCost().multiply(quality);
        BigDecimal price = cost.multiply(new BigDecimal("1.8"));
        if ("PRICE".equals(policy)) {
            BigDecimal cheapest = null;
            for (StrategySnapshot.Competitor competitor : snapshot.competitors()) {
                BigDecimal competitorPrice = competitor.price();
                if (cheapest == null || competitorPrice.compareTo(cheapest) < 0) {
                    cheapest = competitorPrice;
                }
            }
            if (cheapest != null) {
                price = cheapest.multiply(new BigDecimal("0.95"));
            }
        }

        BigDecimal minimumPrice = cost.multiply(new BigDecimal("1.05"));
        price = price.max(minimumPrice);
        price = price.setScale(2, RoundingMode.HALF_UP);

        // Все три политики работают без рекламы; ассортимент не может превышать партию.
        assortment = Math.min(count, assortment);
        CommonProductionParameters plan = new CommonProductionParameters();
        plan.setProductCount(count);
        plan.setPrice(price);
        plan.setQualityIndex(quality);
        plan.setAssortment(assortment);
        plan.setAdvertisingIntensityIndex(0);
        plan.setAdvertisingDays(0);
        return new BotDecision(plan);
    }
}
