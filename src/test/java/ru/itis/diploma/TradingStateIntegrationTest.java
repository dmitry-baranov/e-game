package ru.itis.diploma;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.diploma.model.*;
import ru.itis.diploma.model.enums.GameStatus;
import ru.itis.diploma.repository.*;
import ru.itis.diploma.service.TradingSessionService;
import ru.itis.diploma.service.StrategyCoordinator;
import ru.itis.diploma.service.StrategySnapshotService;
import ru.itis.diploma.service.StrategyService;
import ru.itis.diploma.dto.StrategyPlan;
import ru.itis.diploma.dto.InitialProductionParameters;
import ru.itis.diploma.service.ManufacturerService;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class TradingStateIntegrationTest {
    @Autowired private GameRepository games;
    @Autowired private AccountRepository accounts;
    @Autowired private ManufacturerRepository manufacturers;
    @Autowired private ProductionParametersRepository production;
    @Autowired private InvestmentCreditPaymentRepository investment;
    @Autowired private TradingSessionService sessions;
    @Autowired private StrategySnapshotService snapshots;
    @Autowired private StrategyCoordinator coordinator;
    @Autowired private StrategyService strategyService;
    @Autowired private RecommendationHistoryRepository recommendations;
    @Autowired private StrategyTrainingEpisodeRepository trainingEpisodes;
    @Autowired private ManufacturerService manufacturerService;
    @Autowired private EntityManager entityManager;
    @Autowired private TradingSessionResultsRepository purchases;

    @Test
    @Transactional
    void wornOutPurchasesAreUpdatedAtBoundaryOnlyWithinTheirGame() {
        var first = games.save(Game.builder().name("Wear A").status(GameStatus.STARTED).currentDay(10).build());
        var second = games.save(Game.builder().name("Wear B").status(GameStatus.STARTED).currentDay(10).build());
        var owner = manufacturers.save(Manufacturer.builder().game(first).build());
        var other = manufacturers.save(Manufacturer.builder().game(second).build());
        var expired = purchases.save(TradingSessionResults.builder().manufacturer(owner).tradeDate(0)
            .productNumber(1).qualityIndex(BigDecimal.ONE).isWornOut(false).build());
        var fresh = purchases.save(TradingSessionResults.builder().manufacturer(owner).tradeDate(1)
            .productNumber(1).qualityIndex(BigDecimal.ONE).isWornOut(false).build());
        var zero = purchases.save(TradingSessionResults.builder().manufacturer(owner).tradeDate(0)
            .productNumber(0).qualityIndex(BigDecimal.ONE).isWornOut(false).build());
        var foreign = purchases.save(TradingSessionResults.builder().manufacturer(other).tradeDate(0)
            .productNumber(1).qualityIndex(BigDecimal.ONE).isWornOut(false).build());
        entityManager.flush(); entityManager.clear();
        assertEquals(1, purchases.markWornOutByGameId(first.getId(), 10, 10));
        entityManager.flush(); entityManager.clear();
        assertTrue(purchases.findById(expired.getId()).orElseThrow().getIsWornOut());
        assertFalse(purchases.findById(fresh.getId()).orElseThrow().getIsWornOut());
        assertFalse(purchases.findById(zero.getId()).orElseThrow().getIsWornOut());
        assertFalse(purchases.findById(foreign.getId()).orElseThrow().getIsWornOut());
    }

    @Test
    @Transactional
    void trainingConsentCapturesPreDecisionOnlyAndRevocationRemovesEpisode() {
        var game = games.save(Game.builder().name("ML").status(GameStatus.CREATED).currentDay(0)
            .baseCostPrice(BigDecimal.TEN).baseAdvertisementPrice(BigDecimal.ONE).productPower(BigDecimal.TEN)
            .salesTax(BigDecimal.TEN).interestRateInvestmentCredit(BigDecimal.TEN)
            .interestRateBusinessCredit(BigDecimal.TEN).investmentCreditTermMonths(BigDecimal.TEN)
            .purchaseLimit(999).build());
        var account = accounts.save(Account.builder().email("ml-consent@example.test").build());
        var owner = manufacturers.save(Manufacturer.builder().game(game).account(account)
            .currentProductCount(0).build());
        var action = new InitialProductionParameters();
        action.setProductCount(10); action.setProductionCapacityPerDay(10);
        action.setPrice(BigDecimal.TEN); action.setQualityIndex(BigDecimal.ONE);
        action.setAssortment(1); action.setAdvertisingIntensityIndex(0); action.setAdvertisingDays(0);
        manufacturerService.defineInitialProductionParameters(action, account.getId(), game);
        assertEquals(0, trainingEpisodes.countByManufacturerId(owner.getId()));

        // Independent of recommendation history; an opted-in player starts a new game.
        var game2 = games.save(Game.builder().name("ML2").status(GameStatus.CREATED).currentDay(0)
            .baseCostPrice(BigDecimal.TEN).baseAdvertisementPrice(BigDecimal.ONE).productPower(BigDecimal.TEN)
            .salesTax(BigDecimal.TEN).interestRateInvestmentCredit(BigDecimal.TEN)
            .interestRateBusinessCredit(BigDecimal.TEN).investmentCreditTermMonths(BigDecimal.TEN)
            .purchaseLimit(999).build());
        var opted = manufacturers.save(Manufacturer.builder().game(game2).account(account)
            .allowStrategyTraining(true).saveRecommendationHistory(false).currentProductCount(0).build());
        manufacturerService.defineInitialProductionParameters(action, account.getId(), game2);
        var saved = trainingEpisodes.findAll().stream()
            .filter(e -> e.getManufacturer().getId().equals(opted.getId())).toList();
        assertEquals(1, saved.size());
        assertFalse(saved.get(0).getSnapshotJson().contains("purchaseLimit"));
        assertTrue(saved.get(0).getSnapshotJson().contains("\"opened\":false"));
        assertFalse(saved.get(0).getActionJson().contains("account"));
        strategyService.trainingPreference(game2.getId(), account.getId(), false);
        entityManager.flush(); entityManager.clear();
        assertEquals(0, trainingEpisodes.countByManufacturerId(opted.getId()));
        assertFalse(manufacturers.findById(opted.getId()).orElseThrow().getAllowStrategyTraining());
        assertTrue(recommendations.findByManufacturerIdOrderByCreatedAtDesc(opted.getId()).isEmpty());
    }

    @Test
    @Transactional
    void tradingDayPersistsInventoryAndDayTogether() {
        var game = Game.builder().name("Test").currentDay(0).status(GameStatus.STARTED)
            .timeUnit(60).purchaseLimit(5).habitTrackingDays(7).dailySpendingLimit(new BigDecimal("100"))
            .absoluteQualityProductLife(10).assortmentWeight(BigDecimal.ONE).qualityWeight(BigDecimal.ONE)
            .advertisementWeight(BigDecimal.ONE).habitWeight(BigDecimal.ONE).salesTax(BigDecimal.TEN)
            .investmentCreditTermMonths(new BigDecimal("12"))
            .interestRateInvestmentCredit(BigDecimal.TEN).interestRateBusinessCredit(BigDecimal.TEN)
            .build();
        game = games.save(game);
        var account = accounts.save(Account.builder().email("trading-state@example.test").build());
        var manufacturer = manufacturers.save(Manufacturer.builder().game(game).account(account)
            .enteredInitialProductionParameters(true).balance(new BigDecimal("100"))
            .currentProductCount(0).investmentCreditDebt(BigDecimal.ZERO)
            .investmentCreditTermMonths(new BigDecimal("12")).productionCapacityPerDay(10).build());
        production.save(ProductionParameters.builder().manufacturer(manufacturer).startDate(0)
            .timeToMarket(1).productCount(10).productionCapacityPerDay(10).qualityIndex(BigDecimal.ONE)
            .assortment(1).price(new BigDecimal("2")).costPrice(BigDecimal.ONE).build());
        investment.save(InvestmentCreditPayment.builder().manufacturer(manufacturer).date(0).nextDate(30)
            .principalPayment(BigDecimal.ZERO).interestAmount(BigDecimal.ZERO).build());
        entityManager.flush();
        sessions.doDaysActivities(game);
        entityManager.flush();
        entityManager.clear(); // Read from storage, not the simulation's in-memory objects.
        assertEquals(1, games.findById(game.getId()).orElseThrow().getCurrentDay());
        var persisted = manufacturers.findById(manufacturer.getId()).orElseThrow();
        assertEquals(5, persisted.getCurrentProductCount());
        assertEquals(0, new BigDecimal("110").compareTo(persisted.getBalance()));
        sessions.doDaysActivities(Game.builder().id(game.getId()).currentDay(0).build());
        entityManager.flush(); entityManager.clear();
        assertEquals(1, games.findById(game.getId()).orElseThrow().getCurrentDay());
        assertEquals(5, manufacturers.findById(manufacturer.getId()).orElseThrow().getCurrentProductCount());
    }

    @Test
    @Transactional
    void hiddenBuyerSettingsDoNotInfluenceStarterAdvice() {
        var game = games.save(Game.builder().name("Start").status(GameStatus.CREATED).currentDay(0)
            .baseCostPrice(new BigDecimal("10")).baseAdvertisementPrice(BigDecimal.ONE)
            .productPower(BigDecimal.TEN).salesTax(BigDecimal.TEN)
            .interestRateInvestmentCredit(BigDecimal.TEN).interestRateBusinessCredit(BigDecimal.TEN)
            .investmentCreditTermMonths(new BigDecimal("12"))
            .purchaseLimit(1).habitWeight(BigDecimal.ONE).build());
        var account = accounts.save(Account.builder().email("starter@example.test").build());
        manufacturers.save(Manufacturer.builder().game(game).account(account).currentProductCount(0).build());
        var before = coordinator.recommend(snapshots.snapshot(game.getId(), account.getId()), null).getRecommendations();
        game.setPurchaseLimit(999);
        game.setHabitWeight(new BigDecimal("30"));
        game.setQualityWeight(new BigDecimal("25"));
        game.setDailySpendingLimit(new BigDecimal("10000"));
        games.saveAndFlush(game);
        var after = coordinator.recommend(snapshots.snapshot(game.getId(), account.getId()), null).getRecommendations();
        assertEquals(before, after);
    }

    @Test
    @Transactional
    void suggestionsOnlyReadGameWhileOptInHistoryAndChoicePersistSeparately() {
        var game = games.save(Game.builder().name("Choice").status(GameStatus.CREATED).currentDay(0)
            .baseCostPrice(new BigDecimal("10")).baseAdvertisementPrice(BigDecimal.ONE)
            .productPower(BigDecimal.TEN).salesTax(BigDecimal.TEN)
            .investmentCreditTermMonths(new BigDecimal("12"))
            .interestRateInvestmentCredit(BigDecimal.TEN).interestRateBusinessCredit(BigDecimal.TEN).build());
        var account = accounts.save(Account.builder().email("choice@example.test").build());
        var owner = manufacturers.save(Manufacturer.builder().game(game).account(account)
            .currentProductCount(0).saveRecommendationHistory(false).build());
        var first = strategyService.recommend(game.getId(), account.getId(), UUID.randomUUID().toString(), null);
        assertEquals(3, first.getRecommendations().size());
        assertTrue(recommendations.findByManufacturerIdOrderByCreatedAtDesc(owner.getId()).isEmpty());
        strategyService.historyPreference(game.getId(), account.getId(), true);
        String key = UUID.randomUUID().toString();
        var stored = strategyService.recommend(game.getId(), account.getId(), key, null);
        assertEquals(stored.getHistoryId(), strategyService.recommend(game.getId(), account.getId(), key, null).getHistoryId());
        assertEquals(1, recommendations.findByManufacturerIdOrderByCreatedAtDesc(owner.getId()).size());
        strategyService.select(game.getId(), account.getId(), "CAUTIOUS", stored.getHistoryId());
        entityManager.flush(); entityManager.clear();
        var reloaded = manufacturers.findById(owner.getId()).orElseThrow();
        assertEquals("CAUTIOUS", reloaded.getSelectedStrategy());
        assertFalse(reloaded.isEnteredInitialProductionParameters());
        assertTrue(production.findByManufacturerId(owner.getId()).isEmpty());
        assertEquals(0, games.findById(game.getId()).orElseThrow().getCurrentDay());
        reloaded.setEnteredInitialProductionParameters(true);
        reloaded.setBalance(new BigDecimal("100"));
        reloaded.setProductionCapacityPerDay(10);
        manufacturers.save(reloaded);
        production.save(ProductionParameters.builder().manufacturer(reloaded).startDate(0)
            .timeToMarket(1).productCount(10).productionCapacityPerDay(10)
            .qualityIndex(new BigDecimal("0.5")).assortment(1).price(new BigDecimal("8"))
            .costPrice(new BigDecimal("5")).build());
        assertNotNull(strategyService.recommend(game.getId(), account.getId(), UUID.randomUUID().toString(), null)
            .getCourseMismatch());
    }

    @Test
    @Transactional
    void personalPlanSurvivesDisabledHistoryAndNeverOpensProduction() {
        var game = games.save(Game.builder().name("Personal").status(GameStatus.CREATED).currentDay(0)
            .baseCostPrice(new BigDecimal("10")).baseAdvertisementPrice(BigDecimal.ONE)
            .productPower(BigDecimal.TEN).salesTax(BigDecimal.TEN)
            .investmentCreditTermMonths(new BigDecimal("12"))
            .interestRateInvestmentCredit(BigDecimal.TEN).interestRateBusinessCredit(BigDecimal.TEN).build());
        var account = accounts.save(Account.builder().email("personal@example.test").build());
        var owner = manufacturers.save(Manufacturer.builder().game(game).account(account)
            .currentProductCount(0).saveRecommendationHistory(false).build());
        var plan = new StrategyPlan();
        plan.setName("Мой осторожный план"); plan.setProductCount(12); plan.setCapacity(10);
        plan.setPrice(new BigDecimal("8")); plan.setQuality(new BigDecimal("0.5"));
        plan.setAssortment(2); plan.setAdvertisingIntensity(0); plan.setAdvertisingDays(0);
        Long id = strategyService.save(game.getId(), account.getId(), plan);
        var saved = strategyService.plan(game.getId(), account.getId(), id);
        var advice = strategyService.recommend(game.getId(), account.getId(), UUID.randomUUID().toString(), saved);
        assertEquals("PERSONAL:" + id, advice.getRecommendations().get(0).getKey());
        assertTrue(recommendations.findByManufacturerIdOrderByCreatedAtDesc(owner.getId()).isEmpty());
        strategyService.select(game.getId(), account.getId(), "PERSONAL:" + id, null);
        entityManager.flush(); entityManager.clear();
        assertEquals("PERSONAL:" + id, manufacturers.findById(owner.getId()).orElseThrow().getSelectedStrategy());
        assertTrue(production.findByManufacturerId(owner.getId()).isEmpty());
        strategyService.delete(game.getId(), account.getId(), id);
        assertNull(manufacturers.findById(owner.getId()).orElseThrow().getSelectedStrategy());
    }
}
