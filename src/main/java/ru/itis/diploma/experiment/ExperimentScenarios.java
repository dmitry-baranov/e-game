package ru.itis.diploma.experiment;

import org.springframework.stereotype.Component;
import ru.itis.diploma.dto.CreateGameDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.SplittableRandom;

@Component
public class ExperimentScenarios {
    public static final String VERSION = "market-v4";
    public static final List<String> MARKETS = List.of("BUDGET", "COMPETITIVE", "ADVERTISING",
        "QUALITY_FOCUS", "LOYALTY", "CREDIT_CRUNCH", "PERISHABLE", "OVERSUPPLY");
    private static final String[] NAMES = {"Бюджетный", "Конкурентный", "Дорогая реклама",
        "Ставка на качество", "Лояльный покупатель", "Дорогие кредиты", "Быстрый износ",
        "Избыточное предложение"};
    public record Scenario(long seed, String market, int bots, int horizon, CreateGameDto game) {}

    // A game's seed never depends on worker order or database-generated IDs.
    public Scenario generate(ExperimentConfig c, int number) {
        return generate(c, number, VERSION);
    }

    public Scenario generate(ExperimentConfig c, int number, String version) {
        c.validate();
        int scenarioNumber = "EVALUATE".equals(c.getMode()) ? (number + 1) / 2 : number;
        long seed = new SplittableRandom(c.getSeed() ^ (0x9e3779b97f4a7c15L * scenarioNumber)).nextLong();
        var rng = new SplittableRandom(seed);
        int bots = rng.nextInt(c.getMinBots(), c.getMaxBots() + 1);
        String[] types = c.markets();
        String picked = types[rng.nextInt(types.length)];
        int type = MARKETS.indexOf(picked);
        if (type < 0 || (type >= 3 && !VERSION.equals(version)))
            throw new IllegalArgumentException("Рынок не поддерживается генератором " + version + ": " + picked);
        // New markets start from the old competitive scenario; v1–v3 keep their exact RNG and formulas.
        int baseType = type < 3 ? type : 1;
        boolean improved = "market-v2".equals(version) || "market-v3".equals(version) || VERSION.equals(version);
        int lifetime = VERSION.equals(version) && type == 6 ? rng.nextInt(4, 8) :
            improved ? rng.nextInt(8, 17) : rng.nextInt(12, 25);
        int targetDays = VERSION.equals(version) && type == 6 ? lifetime * 7 + 10 :
            ("market-v3".equals(version) || VERSION.equals(version)) ? lifetime * 3 + 10 + baseType * 5 :
                lifetime * 2 + 15;
        int horizon = Math.min(c.getMaxDays(), Math.max(c.getMinDays(), Math.max(45, targetDays)));
        int cost = rng.nextInt(7, 14) + baseType * 2;
        int price = type == 2 ? 4 : 2;
        int capacity = bots * (improved ? rng.nextInt(18, 28) : rng.nextInt(7, 13));
        int purchaseLimit = VERSION.equals(version) && type == 7 ? Math.max(1, capacity / 3) : capacity;
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
