package ru.itis.diploma.service;

import freemarker.template.Configuration;
import org.junit.jupiter.api.Test;
import ru.itis.diploma.dto.StrategyPlan;
import ru.itis.diploma.dto.StrategySnapshot;
import ru.itis.diploma.model.Manufacturer;
import ru.itis.diploma.model.Account;
import ru.itis.diploma.model.Game;
import ru.itis.diploma.model.enums.GameStatus;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategyTemplateTest {
    @Test
    void rendersStartingAndOperatingPagesWithoutDatabase() throws Exception {
        var config = new Configuration(Configuration.VERSION_2_3_31);
        config.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "/templates");
        var owner = Manufacturer.builder().saveRecommendationHistory(false).build();
        for (boolean opened : List.of(false, true)) {
            var snapshot = new StrategySnapshot(1, 0, opened, BigDecimal.ZERO, 0,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN,
                BigDecimal.TEN, 12, opened ? 10 : null, opened ? 20 : null, opened ? 2 : null,
                opened ? new BigDecimal("0.5") : null, opened ? BigDecimal.TEN : null, 0, 0,
                List.of(), List.of(), List.of(), null);
            var writer = new StringWriter();
            var data = new HashMap<String, Object>(Map.of("gameId", 1, "snapshot", snapshot,
                "owner", owner, "plans", List.of(), "history", List.of(), "requestKey", "request",
                "plan", new StrategyPlan()));
            data.put("advice", new StrategyCoordinator(new StrategyMarketAgent(), new StrategyProductionAgent(),
                new StrategyFinancialAgent(), (s, c) -> java.util.Optional.empty()).recommend(snapshot, null));
            config.getTemplate("strategies.ftlh").process(data, writer);
            assertTrue(writer.toString().contains("Торговые стратегии"));
            assertTrue(writer.toString().contains("Осторожный старт") || opened);
        }
    }

    @Test
    void gamePageRendersPlayersDailyResultsAndPayments() throws Exception {
        var config = new Configuration(Configuration.VERSION_2_3_31);
        config.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "/templates");
        var game = Game.builder().id(1L).status(GameStatus.FINISHED).currentDay(1).timeUnit(1)
            .productPower(BigDecimal.TEN).salesTax(BigDecimal.TEN)
            .investmentCreditTermMonths(new BigDecimal("12"))
            .interestRateInvestmentCredit(BigDecimal.TEN).build();
        var snapshot = new StrategySnapshot(1, 1, true, BigDecimal.TEN, 5,
            BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN,
            BigDecimal.TEN, BigDecimal.TEN, 12, 10, 10, 1, BigDecimal.ONE,
            BigDecimal.TEN, 0, 1, List.of(), List.of(),
            List.of(new StrategySnapshot.Payment(30, BigDecimal.TEN, "Оборотный кредит")), null);
        var writer = new StringWriter();
        config.getTemplate("game.ftlh").process(Map.of("game", game, "account", Account.builder().id(2L).build(),
            "manufacturer", Manufacturer.builder().id(3L).build(), "productionParameters", List.of(),
            "gameResults", List.of(), "ownSnapshot", snapshot,
            "dailySales", List.of(new StrategySnapshot.Sale(1, 5, 10, 5))), writer);
        assertTrue(writer.toString().contains("Сейчас на складе: 5"));
        assertTrue(writer.toString().contains("Оборотный кредит"));
    }
}
