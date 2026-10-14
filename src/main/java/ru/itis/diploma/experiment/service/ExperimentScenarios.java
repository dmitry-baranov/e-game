package ru.itis.diploma.experiment.service;

import ru.itis.diploma.experiment.config.ExperimentConfig;

import org.springframework.stereotype.Component;
import ru.itis.diploma.game.dto.CreateGameDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Детерминированный «рецепт» игры: из seed серии и номера попытки получает рынок,
 * число ботов, горизонт и параметры CreateGameDto. Тип рынка — набор параметров
 * обычной игры, НЕ новая реализация покупателя; таблица значений есть в bot/README.md.
 * Версия сохраняется в серии, чтобы следующие игры после паузы создавались прежним
 * генератором. Уже сохранённые игры вообще не пересоздаются.
 */
@Component
public class ExperimentScenarios {
    public static final String VERSION = "market-v5";
    public static final List<String> MARKETS = List.of("BUDGET", "COMPETITIVE", "ADVERTISING",
        "QUALITY_FOCUS", "LOYALTY", "CREDIT_CRUNCH", "PERISHABLE", "OVERSUPPLY");
    private static final String[] NAMES = {"Бюджетный", "Конкурентный", "Дорогая реклама",
        "Ставка на качество", "Лояльный покупатель", "Дорогие кредиты", "Быстрый износ",
        "Избыточное предложение"};
    /** Имена, сохраняемые в experiment_run.market: фильтр статистики использует тот же справочник. */
    public static final List<String> MARKET_NAMES = List.of(NAMES);
    public record Scenario(long seed, String market, int bots, int horizon, CreateGameDto game) {}

    /**
     * Создаёт сценарий текущей версии генератора для новой попытки серии.
     *
     * @param c конфигурация экспериментальной серии
     * @param number порядковый номер попытки
     * @return детерминированно сформированный сценарий игры
     */
    public Scenario generate(ExperimentConfig c, int number) {
        return generate(c, number, VERSION);
    }

    /**
     * Детерминированно создаёт сценарий указанной версии; сохранённая версия позволяет
     * продолжать старые серии без изменения последовательности случайных значений.
     *
     * @param c конфигурация экспериментальной серии
     * @param number порядковый номер попытки
     * @param version версия алгоритма генерации рынка
     * @return сформированный сценарий игры
     * @throws IllegalArgumentException если конфигурация некорректна или рынок не поддерживается версией
     */
    public Scenario generate(ExperimentConfig c, int number, String version) {
        c.validate();
        // Два последовательных номера EVALUATE обозначают один сценарий: общие
        // seed, рынок, боты и условия; различается только политика первого бота.
        int scenarioNumber = number;
        if ("EVALUATE".equals(c.getMode())) {
            scenarioNumber = (number + 1) / 2;
        }
        long mixedSeed = c.getSeed() ^ (0x9e3779b97f4a7c15L * scenarioNumber);
        long seed = new SplittableRandom(mixedSeed).nextLong();
        var rng = new SplittableRandom(seed);
        int bots = rng.nextInt(c.getMinBots(), c.getMaxBots() + 1);
        String[] types = c.markets();
        int pickedIndex = rng.nextInt(types.length);
        String picked = types[pickedIndex];
        int type = MARKETS.indexOf(picked);
        boolean modernMarkets = "market-v4".equals(version) || VERSION.equals(version);
        if (type < 0 || (type >= 3 && !modernMarkets)) {
            throw new IllegalArgumentException("Рынок не поддерживается генератором " + version + ": " + picked);
        }
        // Новые типы наследуют параметры конкурентного сценария. Для v1–v3 нельзя
        // менять ни формулы, ни порядок вызовов rng: это нарушит воспроизводимость.
        int baseType = type < 3 ? type : 1;
        boolean improved = "market-v2".equals(version) || "market-v3".equals(version) || modernMarkets;
        int lifetime;
        if (modernMarkets && type == 6) {
            lifetime = rng.nextInt(4, 8);
        } else if (improved) {
            lifetime = rng.nextInt(8, 17);
        } else {
            lifetime = rng.nextInt(12, 25);
        }

        int targetDays;
        if (modernMarkets && type == 6) {
            targetDays = lifetime * 7 + 10;
        } else if ("market-v3".equals(version) || modernMarkets) {
            targetDays = lifetime * 3 + 10 + baseType * 5;
        } else {
            targetDays = lifetime * 2 + 15;
        }

        int horizon;
        if (VERSION.equals(version)) {
            var horizonRng = new SplittableRandom(seed ^ 0x6a09e667f3bcc909L);
            horizon = horizonRng.nextInt(c.getMinDays(), c.getMaxDays() + 1);
        } else {
            int minimumHorizon = Math.max(45, targetDays);
            horizon = Math.min(c.getMaxDays(), Math.max(c.getMinDays(), minimumHorizon));
        }

        int cost = rng.nextInt(7, 14) + baseType * 2;
        int price = type == 2 ? 4 : 2;
        int capacityPerBot = improved ? rng.nextInt(18, 28) : rng.nextInt(7, 13);
        int capacity = bots * capacityPerBot;
        int purchaseLimit = modernMarkets && type == 7 ? Math.max(1, capacity / 3) : capacity;

        int investmentRate = type == 5 ? 18 : 5 + baseType * 2;
        int businessRate = type == 5 ? 24 : 6 + baseType * 2;
        int powerCost = type == 5 ? 60 : 30 + baseType * 10;
        int budgetMultiplier = type == 0 ? 2 : 3;

        CreateGameDto game = new CreateGameDto();
        game.setName("Эксперимент #" + number + " — " + NAMES[type]);
        game.setTimeUnit(1);
        game.setInterestRateInvestmentCredit(bd(investmentRate));
        game.setInterestRateBusinessCredit(bd(businessRate));
        game.setInvestmentCreditTermMonths(bd(12));
        game.setSalesTax(bd(4 + baseType));
        game.setBaseCostPrice(bd(cost));
        game.setBaseAdvertisementPrice(bd(price));
        game.setProductPower(bd(powerCost));
        game.setAssortmentWeight(bd(1));
        game.setQualityWeight(bd(type == 3 ? 6 : 3));
        game.setAdvertisementWeight(bd(type == 2 ? 2 : 1));
        game.setHabitWeight(bd(type == 4 ? 10 : 1));
        game.setHabitTrackingDays(type == 4 ? 20 : 10);
        game.setPurchaseLimit(purchaseLimit);
        game.setDailySpendingLimit(bd(capacity * cost * budgetMultiplier));
        game.setAbsoluteQualityProductLife(lifetime);

        return new Scenario(seed, NAMES[type], bots, horizon, game);
    }

    /**
     * Преобразует целочисленный параметр сценария в десятичное значение без потери точности.
     *
     * @param n исходное целое число
     * @return десятичное представление числа
     */
    private static BigDecimal bd(int n) { return BigDecimal.valueOf(n); }
}
