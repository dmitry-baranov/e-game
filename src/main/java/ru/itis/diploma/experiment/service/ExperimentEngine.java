package ru.itis.diploma.experiment.service;

import ru.itis.diploma.experiment.config.ExperimentConfig;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import ru.itis.diploma.game.dto.*;
import ru.itis.diploma.experiment.bot.BotDecisions;
import ru.itis.diploma.experiment.bot.BotParticipants;
import ru.itis.diploma.experiment.bot.model.BotDecision;
import ru.itis.diploma.experiment.model.BotDecisionLog;
import ru.itis.diploma.experiment.model.ExperimentRun;
import ru.itis.diploma.experiment.model.ExperimentSeries;
import ru.itis.diploma.experiment.model.ExperimentPair;
import ru.itis.diploma.experiment.repository.ExperimentPairRepository;
import ru.itis.diploma.experiment.repository.ExperimentRunRepository;
import ru.itis.diploma.experiment.repository.ExperimentSeriesRepository;
import ru.itis.diploma.game.GameSimulation;
import ru.itis.diploma.game.model.*;
import ru.itis.diploma.game.model.enums.GameStatus;
import ru.itis.diploma.game.repository.*;
import ru.itis.diploma.game.service.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Проводит автоматические игры обычными игровыми сервисами. claim выдаёт попытку
 * под блокировкой серии; advance выполняет один игровой день в одной транзакции.
 * Боты внутри дня обрабатываются последовательно, но их снимки фиксируются
 * ДО применения любого плана: соперники не видят будущее решение друг друга.
 */
@Service @RequiredArgsConstructor
public class ExperimentEngine {
    private final TransactionTemplate transactions;
    private final ExperimentRunRepository runs;
    private final ExperimentSeriesRepository series;
    private final ExperimentPairRepository pairs;
    private final ExperimentScenarios scenarios;
    private final BotParticipants botParticipants;
    private final ManufacturerRepository manufacturers;
    private final GameRepository games;
    private final GameResultRepository results;
    private final GameSimulation gameSimulation;
    private final ManufacturerService manufacturerService;
    private final GameDayReadModel gameDayReadModel;
    private final StrategyTrainingCapture trainingCapture;
    private final BotDecisions botDecisions;
    private final ExperimentDecisionJournal journal;
    private final ProductionParametersRepository productions;

    /**
     * Выдача игры потоку. Под блокировкой серии проверяются цель и параллелизм:
     * сначала продолжаем QUEUED попытку с прежней игрой, затем при необходимости
     * создаём новую по сохранённой версии генератора. Завершённая оценочная игра
     * ждёт партнёра в WAITING_PAIR и не считается пригодной отдельно от него.
     *
     * @param seriesId идентификатор серии
     * @param config конфигурация серии, определяющая предел параллелизма и попыток
     * @return идентификатор назначенного запуска или {@code null}, если доступной работы нет
     */
    public Long claim(Long seriesId, ExperimentConfig config) {
        return transactions.execute(tx -> {
            var group = series.lockById(seriesId).orElseThrow();
            if (!"RUNNING".equals(group.getStatus())) return null;
            if ("EVALUATE".equals(group.getMode())) {
                group.setStatus("PAUSED");
                return null;
            }
            int target = group.getTarget();
            long done = runs.countBySeriesIdAndStatus(seriesId, "COMPLETED");
            long active = runs.countBySeriesIdAndStatus(seriesId, "RUNNING");
            long waiting = runs.countBySeriesIdAndStatus(seriesId, "WAITING_PAIR");
            if (done >= target) {
                if (active == 0) {
                    group.setStatus("DONE");
                }
                return null;
            }
            if (active >= config.getParallel()) {
                return null;
            }
            var queued = runs.findFirstBySeriesIdAndStatusOrderByNumberDesc(seriesId, "QUEUED");
            if (queued.isPresent()) {
                ExperimentRun run = queued.get();
                run.setStatus("RUNNING");
                return run.getId();
            }
            if (done + active + waiting >= target) {
                return null;
            }
            if (group.getNextNumber() > config.getMaxAttempts()) {
                if (active == 0) {
                    group.setStatus("EXHAUSTED");
                }
                return null;
            }
            ExperimentRun run = createRun(group, config);
            return run.getId();
        });
    }

    /**
     * Создаёт и сохраняет очередной запуск, его оценочную пару при необходимости и игровую сессию.
     * Метод вызывается внутри транзакции выдачи запуска при удержании блокировки серии.
     *
     * @param group серия, для которой создаётся запуск
     * @param config сохранённая конфигурация серии
     * @return созданный экспериментальный запуск
     * @throws IllegalStateException если условия уже созданной оценочной пары не совпали со сценарием
     */
    private ExperimentRun createRun(ExperimentSeries group, ExperimentConfig config) {
        int number = group.getNextNumber();
        group.setNextNumber(number + 1);
        var scenario = scenarios.generate(config, number, group.getGeneratorVersion());
        var run = new ExperimentRun();
        run.setSeries(group);
        run.setNumber(number);
        if ("EVALUATE".equals(group.getMode())) {
            int pairNumber = (number + 1) / 2;
            var pair = pairs.findBySeriesIdAndPairNumber(group.getId(), pairNumber).orElseGet(() -> {
                var created = new ExperimentPair();
                created.setSeries(group);
                created.setPairNumber(pairNumber);
                created.setSeed(scenario.seed());
                created.setMarket(scenario.market());
                return pairs.save(created);
            });
            if (pair.getSeed() != scenario.seed() || !pair.getMarket().equals(scenario.market()))
                throw new IllegalStateException("Условия EVALUATE-пары изменились");
            run.setPair(pair);
            run.setSeed(pair.getSeed());
            run.setMarket(pair.getMarket());
        } else {
            run.setSeed(scenario.seed());
            run.setMarket(scenario.market());
        }
        run.setBots(scenario.bots());
        run.setHorizon(scenario.horizon());
        runs.save(run);
        String[] policies = config.policies();
        Game game = botParticipants.create(config.getMode(), policies, number, scenario.bots(), scenario.game());
        run.setGame(game);
        return run;
    }

    /**
     * Один атомарный шаг: блокировка игры → начальное открытие или выбор новых
     * планов → проверка/журналирование → торговля/платежи → сохранение дня.
     * false означает, что рабочему потоку больше нечего выполнять (пауза, финиш).
     * При исключении транзакция дня откатывается, а Manager решает о повторе.
     *
     * @param runId идентификатор экспериментального запуска
     * @return {@code true}, если один игровой день успешно проведён
     * @throws IllegalStateException если торговая симуляция не продвинула игру ровно на один день
     */
    public boolean advance(Long runId) {
        return Boolean.TRUE.equals(transactions.execute(tx -> {
            var run = runs.findById(runId).orElseThrow();
            if (!"RUNNING".equals(run.getStatus())) {
                return false;
            }
            var group = series.findById(run.getSeries().getId()).orElseThrow();
            if (!"RUNNING".equals(group.getStatus())) {
                stopOrQueue(run, group);
                return false;
            }
            Long gameId = run.getGame().getId();
            Game game = games.lockById(gameId).orElseThrow();
            if (game.getStatus() == GameStatus.CREATED) {
                startGame(run, group, game);
            }
            if (game.getCurrentDay() >= run.getHorizon()) {
                finishGame(run, group, game);
                return false;
            }
            decideAndApplyProduction(run, group, game);
            int before = game.getCurrentDay();
            // Обычный торговый сервис проводит покупателя, кредиты, налоги и продажи.
            // Решения, платёжные записи и статистика дня коммитятся вместе.
            gameSimulation.tradeDay(game);
            if (game.getCurrentDay() != before + 1) {
                throw new IllegalStateException("Торговый день не продвинулся");
            }
            run.setCompletedDays(game.getCurrentDay());
            return true;
        }));
    }

    /**
     * Возвращает запуск в очередь при временной остановке серии либо окончательно отменяет его.
     * При окончательной остановке также инвалидирует пару и завершает игровую сессию.
     *
     * @param run обрабатываемый запуск
     * @param group серия запуска
     */
    private void stopOrQueue(ExperimentRun run, ExperimentSeries group) {
        if (!"STOPPED".equals(group.getStatus())) {
            run.setStatus("QUEUED");
            return;
        }
        run.setStatus("CANCELLED");
        invalidatePair(run);
        Game game = run.getGame();
        if (game.getStatus() != GameStatus.FINISHED) {
            gameSimulation.finish(game);
        }
    }

    private record ProposedDecision(Manufacturer player, StrategySnapshot view,
                                     BotDecision decision, int index) {}

    /**
     * Получает предложение политики для снимка игрока, не применяя его к игровому состоянию.
     *
     * @param player производитель, для которого выбирается действие
     * @param view снимок состояния до решений текущего дня
     * @param index устойчивый индекс производителя в игре
     * @return предложение вместе с контекстом последующего применения
     */
    private ProposedDecision propose(Manufacturer player, StrategySnapshot view, int index) {
        BotDecision decision = botDecisions.decide(player, view, index);
        return new ProposedDecision(player, view, decision, index);
    }

    /**
     * Формирует начальные решения всех ботов по общим снимкам, сохраняет их и открывает игру.
     * Все изменения выполняются в транзакции первого шага игрового дня.
     *
     * @param run запускаемый эксперимент
     * @param group серия с версиями политики и модели
     * @param game создаваемая игровая сессия
     */
    private void startGame(ExperimentRun run, ExperimentSeries group, Game game) {
        Long gameId = game.getId();
        List<Manufacturer> players = ordered(gameId);
        Map<Long, StrategySnapshot> views = gameDayReadModel.snapshotsBeforeDecisions(gameId);
        List<ProposedDecision> proposed = new ArrayList<>();
        // Решения всех ботов вычисляются до применения первого производства.
        for (int i = 0; i < players.size(); i++) {
            var player = players.get(i);
            Long accountId = player.getAccount().getId();
            StrategySnapshot view = views.get(accountId);
            proposed.add(propose(player, view, i));
        }
        trainingCapture.withPreDecisionSnapshots(run.getId(), views, () -> {
            for (var choice : proposed) {
                BotDecisionLog decisionLog = journal.logDecision(run, choice.player(), choice.view(), choice.decision(),
                    "ACCEPTED", group.getModelVersion());
                var initial = new InitialProductionParameters();
                copy(choice.decision().action(), initial);
                initial.setProductionCapacityPerDay(10 + choice.index() % 5);
                Long accountId = choice.player().getAccount().getId();
                ProductionParameters cycle = manufacturerService.defineInitialProductionParameters(
                    initial, accountId, game);
                journal.acceptCycle(decisionLog, cycle);
            }
        });
        game.setStatus(GameStatus.STARTED);
    }

    /**
     * Завершает игру, рассчитывает итоговые показатели и определяет пригодность запуска или пары.
     *
     * @param run завершаемый экспериментальный запуск
     * @param group серия запуска
     * @param game завершившаяся игровая сессия
     */
    private void finishGame(ExperimentRun run, ExperimentSeries group, Game game) {
        gameSimulation.finish(game);
        run.setCompletedDays(game.getCurrentDay());
        List<Manufacturer> players = ordered(game.getId());
        int totalCycles = 0;
        boolean enough = true;
        for (Manufacturer player : players) {
            long cycles = productions.countByManufacturerId(player.getId());
            totalCycles += cycles;
            if (cycles < 2) {
                enough = false;
            }
        }
        run.setCycles(totalCycles);

        BigDecimal bestResult = null;
        for (GameResult result : results.findByGameId(game.getId())) {
            BigDecimal value = result.getResult();
            if (bestResult == null || value.compareTo(bestResult) > 0) {
                bestResult = value;
            }
        }
        run.setBestResult(bestResult == null ? BigDecimal.ZERO : bestResult);
        // Для завершения запуска требуются повторные решения всех ботов.
        completePair(run, group, enough);
    }

    /**
     * Собирает предложенные производственные решения по снимкам до хода и затем последовательно
     * проверяет и применяет их, чтобы ни один бот не видел решения соперников за тот же день.
     *
     * @param run текущий экспериментальный запуск
     * @param group серия с версиями политики и модели
     * @param game активная игровая сессия
     */
    private void decideAndApplyProduction(ExperimentRun run, ExperimentSeries group, Game game) {
        Long gameId = game.getId();
        List<Manufacturer> players = ordered(gameId);
        Map<Long, StrategySnapshot> views = gameDayReadModel.snapshotsBeforeDecisions(gameId);
        List<ProductionParameters> cycles = productions.findByManufacturer_Game_IdOrderByStartDateAscIdAsc(gameId);
        Map<Long, ProductionParameters> latest = new HashMap<>();
        for (ProductionParameters cycle : cycles) {
            latest.put(cycle.getManufacturer().getId(), cycle);
        }
        List<ProposedDecision> proposed = new ArrayList<>();
        // Сначала собираем все снимки и решения; до этого момента никто не делает ход.
        for (int i = 0; i < players.size(); i++) {
            var player = players.get(i);
            var last = latest.get(player.getId());
            if (last == null) {
                continue;
            }
            int readyDay = last.getStartDate() + last.getTimeToMarket() + 1;
            int lastDecisionDay = run.getHorizon() - 5;
            if (game.getCurrentDay() < readyDay || game.getCurrentDay() >= lastDecisionDay) {
                continue;
            }
            Long accountId = player.getAccount().getId();
            StrategySnapshot view = views.get(accountId);
            boolean skipRepeated = "bots-v6".equals(group.getPolicyVersion())
                || "bots-v7".equals(group.getPolicyVersion());
            if (skipRepeated && journal.unchangedAfterSkip(run, player, view)) {
                continue;
            }
            proposed.add(propose(player, view, i));
        }
        Map<Long, StrategySnapshot> candidateViews = new HashMap<>();
        for (ProposedDecision choice : proposed) {
            Long accountId = choice.player().getAccount().getId();
            candidateViews.put(accountId, choice.view());
        }
        trainingCapture.withPreDecisionSnapshots(run.getId(), candidateViews, () -> {
            for (ProposedDecision choice : proposed) {
                applyProduction(run, group, game, choice);
            }
        });
    }

    /**
     * Проверяет предложенный производственный план, журналирует результат и создаёт новый цикл,
     * если ограничения склада и кредита позволяют принять решение.
     *
     * @param run текущий экспериментальный запуск
     * @param group серия с версией правил проверки
     * @param game активная игровая сессия
     * @param choice ранее рассчитанное предложение бота
     */
    private void applyProduction(ExperimentRun run, ExperimentSeries group, Game game, ProposedDecision choice) {
        var player = choice.player();
        var plan = choice.decision().action();
        // Старые версии политик сохраняют историческое поведение без проверки склада.
        if (group.getPolicyVersion() != null && player.getCurrentProductCount() > plan.getProductCount()) {
            journal.logDecision(run, player, choice.view(), choice.decision(), "STOCK_SKIP", group.getModelVersion());
            run.setStockSkips(run.getStockSkips() + 1);
            return;
        }
        BigDecimal unitCost = game.getBaseCostPrice().multiply(plan.getQualityIndex());
        BigDecimal productionCost = unitCost.multiply(BigDecimal.valueOf(plan.getProductCount()));
        int advertisingUnits = plan.getAdvertisingIntensityIndex() * plan.getAdvertisingDays();
        BigDecimal advertisingCost = game.getBaseAdvertisementPrice().multiply(BigDecimal.valueOf(advertisingUnits));
        BigDecimal totalCost = productionCost.add(advertisingCost);
        BigDecimal shortfall = totalCost.subtract(player.getBalance()).max(BigDecimal.ZERO);
        BigDecimal baseCost = game.getBaseCostPrice();
        BigDecimal creditMultiplier = BigDecimal.valueOf(80);
        BigDecimal creditLimit = baseCost.multiply(creditMultiplier);
        if (shortfall.compareTo(creditLimit) > 0) {
            journal.logDecision(run, player, choice.view(), choice.decision(), "CREDIT_SKIP", group.getModelVersion());
            run.setCreditSkips(run.getCreditSkips() + 1);
            return;
        }
        var next = new NewProductionParameters();
        copy(plan, next);
        next.setBusinessCreditAmount(shortfall);
        var decisionLog = journal.logDecision(run, player, choice.view(), choice.decision(),
            "ACCEPTED", group.getModelVersion());
        var cycle = manufacturerService.defineNewProductionParameters(next, player.getAccount().getId(), game);
        journal.acceptCycle(decisionLog, cycle);
    }

    /**
     * Возвращает производителей игры в устойчивом порядке для воспроизводимой индексации ботов.
     *
     * @param gameId идентификатор игры
     * @return список производителей, отсортированный по идентификатору
     */
    private List<Manufacturer> ordered(Long gameId) {
        List<Manufacturer> players = manufacturers.findByGame_Id(gameId);
        return players.stream().sorted(Comparator.comparing(Manufacturer::getId)).toList();
    }

    /**
     * Копирует управляемые ботом параметры производственного плана в DTO игрового сервиса.
     *
     * @param source рассчитанный ботом план
     * @param target изменяемый набор параметров игрового сервиса
     */
    private static void copy(CommonProductionParameters source, CommonProductionParameters target) {
        target.setProductCount(source.getProductCount());
        target.setPrice(source.getPrice());
        target.setQualityIndex(source.getQualityIndex());
        target.setAssortment(source.getAssortment());
        target.setAdvertisingIntensityIndex(source.getAdvertisingIntensityIndex());
        target.setAdvertisingDays(source.getAdvertisingDays());
    }

    /**
     * Завершённый COLLECT-запуск всегда пригоден: неполные циклы отдельно отсеиваются при обучении.
     * Для EVALUATE обе половины становятся COMPLETED только после удачного завершения партнёра;
     * если одна непригодна, в статистику эффекта не должна попасть вся пара.
     *
     * @param run завершённый запуск
     * @param group серия запуска
     * @param valid признак соответствия запуска критериям пригодности
     */
    private void completePair(ExperimentRun run, ExperimentSeries group, boolean valid) {
        if (!"EVALUATE".equals(group.getMode())) {
            run.setStatus("COMPLETED");
            return;
        }
        var pair = pairs.lockById(run.getPair().getId()).orElseThrow();
        pair.finish(run, runs.findByPairId(pair.getId()), valid);
    }

    /**
     * Помечает оценочную пару запуска непригодной; для одиночного запуска ничего не делает.
     * Вызывается внутри транзакции изменения состояния запуска.
     *
     * @param run отменяемый или ошибочный запуск
     */
    private void invalidatePair(ExperimentRun run) {
        if (run.getPair() == null) return;
        var pair = pairs.lockById(run.getPair().getId()).orElseThrow();
        pair.invalidate(runs.findByPairId(pair.getId()));
    }
}
