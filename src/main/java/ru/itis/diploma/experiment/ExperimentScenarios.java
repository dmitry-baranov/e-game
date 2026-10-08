package ru.itis.diploma.experiment;

import org.springframework.stereotype.Component;
import ru.itis.diploma.dto.CreateGameDto;
import java.math.BigDecimal;
import java.util.SplittableRandom;

@Component
public class ExperimentScenarios {
    public record Scenario(long seed, String market, int bots, int horizon, CreateGameDto game) {}

    // A game's seed never depends on worker order or database-generated IDs.
    public Scenario generate(ExperimentConfig c, int number) {
        return generate(c, number, "market-v3");
    }

    public Scenario generate(ExperimentConfig c, int number, String version) {
        c.validate();
        int scenarioNumber = "EVALUATE".equals(c.getMode()) ? (number + 1) / 2 : number;
        long seed = new SplittableRandom(c.getSeed() ^ (0x9e3779b97f4a7c15L * scenarioNumber)).nextLong();
        var rng = new SplittableRandom(seed);
        int bots = rng.nextInt(c.getMinBots(), c.getMaxBots() + 1);
        String[] types = c.markets();
        String picked = types[rng.nextInt(types.length)];
        int type = java.util.List.of("BUDGET", "COMPETITIVE", "ADVERTISING").indexOf(picked);
        String market = new String[]{"Бюджетный", "Конкурентный", "Дорогая реклама"}[type];
        boolean improved = "market-v2".equals(version) || "market-v3".equals(version);
        int lifetime = improved ? rng.nextInt(8, 17) : rng.nextInt(12, 25);
        int targetDays = "market-v3".equals(version) ? lifetime * 3 + 10 + type * 5 : lifetime * 2 + 15;
        int horizon = Math.min(c.getMaxDays(), Math.max(c.getMinDays(), Math.max(45, targetDays)));
        int cost = rng.nextInt(7, 14) + type * 2;
        int price = type == 2 ? 4 : 2;
        int capacity = bots * (improved ? rng.nextInt(18, 28) : rng.nextInt(7, 13));
        var g = CreateGameDto.builder().name("Эксперимент #" + number + " — " + market)
            .timeUnit(1).interestRateInvestmentCredit(bd(5 + type * 2))
            .interestRateBusinessCredit(bd(6 + type * 2)).investmentCreditTermMonths(bd(12))
            .salesTax(bd(4 + type)).baseCostPrice(bd(cost))
            .baseAdvertisementPrice(bd(price)).productPower(bd(30 + type * 10))
            .assortmentWeight(bd(1)).qualityWeight(bd(3))
            .advertisementWeight(bd(type == 2 ? 2 : 1)).habitWeight(bd(1))
            .habitTrackingDays(10).purchaseLimit(capacity)
            .dailySpendingLimit(bd(capacity * cost * (type == 0 ? 2 : 3)))
            .absoluteQualityProductLife(lifetime).build();
        return new Scenario(seed, market, bots, horizon, g);
    }

    private static BigDecimal bd(int n) { return BigDecimal.valueOf(n); }
}
