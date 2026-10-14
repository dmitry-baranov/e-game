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

    /** Новые серии используют VERSION; старые передают сохранённую версию в перегрузку ниже. */
    public Scenario generate(ExperimentConfig c, int number) {
        return generate(c, number, VERSION);
    }

    public Scenario generate(ExperimentConfig c, int number, String version) {
        c.validate();
        // Два последовательных номера EVALUATE обозначают один сценарий: общие
        // seed, рынок, боты и условия; различается только политика первого бота.
        int scenarioNumber = "EVALUATE".equals(c.getMode()) ? (number + 1) / 2 : number;
        long seed = new SplittableRandom(c.getSeed() ^ (0x9e3779b97f4a7c15L * scenarioNumber)).nextLong();
        var rng = new SplittableRandom(seed);
        int bots = rng.nextInt(c.getMinBots(), c.getMaxBots() + 1);
        String[] types = c.markets();
        String picked = types[rng.nextInt(types.length)];
        int type = MARKETS.indexOf(picked);
        boolean modernMarkets = "market-v4".equals(version) || VERSION.equals(version);
        if (type < 0 || (type >= 3 && !modernMarkets))
            throw new IllegalArgumentException("Рынок не поддерживается генератором " + version + ": " + picked);
        // Новые типы наследуют параметры конкурентного сценария. Для v1–v3 нельзя
        // менять ни формулы, ни порядок вызовов rng: это нарушит воспроизводимость.
        int baseType = type < 3 ? type : 1;
        boolean improved = "market-v2".equals(version) || "market-v3".equals(version) || modernMarkets;
        int lifetime = modernMarkets && type == 6 ? rng.nextInt(4, 8) :
            improved ? rng.nextInt(8, 17) : rng.nextInt(12, 25);
        int targetDays = modernMarkets && type == 6 ? lifetime * 7 + 10 :
            ("market-v3".equals(version) || modernMarkets) ? lifetime * 3 + 10 + baseType * 5 :
                lifetime * 2 + 15;
        int horizon = VERSION.equals(version)
            ? new SplittableRandom(seed ^ 0x6a09e667f3bcc909L).nextInt(c.getMinDays(), c.getMaxDays() + 1)
            : Math.min(c.getMaxDays(), Math.max(c.getMinDays(), Math.max(45, targetDays)));
        int cost = rng.nextInt(7, 14) + baseType * 2;
        int price = type == 2 ? 4 : 2;
        int capacity = bots * (improved ? rng.nextInt(18, 28) : rng.nextInt(7, 13));
        int purchaseLimit = modernMarkets && type == 7 ? Math.max(1, capacity / 3) : capacity;
        var g = CreateGameDto.builder().name("Эксперимент #" + number + " — " + NAMES[type])
            .timeUnit(1).interestRateInvestmentCredit(bd(type == 5 ? 18 : 5 + baseType * 2))
            .interestRateBusinessCredit(bd(type == 5 ? 24 : 6 + baseType * 2)).investmentCreditTermMonths(bd(12))
            .salesTax(bd(4 + baseType)).baseCostPrice(bd(cost))
            .baseAdvertisementPrice(bd(price)).productPower(bd(type == 5 ? 60 : 30 + baseType * 10))
            .assortmentWeight(bd(1)).qualityWeight(bd(type == 3 ? 6 : 3))
            .advertisementWeight(bd(type == 2 ? 2 : 1)).habitWeight(bd(type == 4 ? 10 : 1))
            .habitTrackingDays(type == 4 ? 20 : 10).purchaseLimit(purchaseLimit)
            .dailySpendingLimit(bd(capacity * cost * (type == 0 ? 2 : 3)))
            .absoluteQualityProductLife(lifetime).build();
        return new Scenario(seed, NAMES[type], bots, horizon, g);
    }

    private static BigDecimal bd(int n) { return BigDecimal.valueOf(n); }
}
