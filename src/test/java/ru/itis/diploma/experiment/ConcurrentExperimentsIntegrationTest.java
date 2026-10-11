package ru.itis.diploma.experiment;

import ru.itis.diploma.experiment.model.ExperimentRun;
import ru.itis.diploma.experiment.repository.ExperimentRunRepository;
import ru.itis.diploma.experiment.service.ExperimentManager;
import ru.itis.diploma.experiment.service.ExperimentScenarios;
import ru.itis.diploma.experiment.config.ExperimentConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.TaskScheduler;
import ru.itis.diploma.game.model.enums.GameStatus;
import ru.itis.diploma.game.repository.GameRepository;
import ru.itis.diploma.game.repository.StatisticsInfoRepository;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"experiment.enabled=true", "experiment.virtual-threads=true",
    "spring.jpa.show-sql=false"})
class ConcurrentExperimentsIntegrationTest {
    @Autowired private ExperimentManager manager;
    @Autowired private ExperimentRunRepository runs;
    @Autowired private GameRepository games;
    @Autowired private StatisticsInfoRepository statistics;
    @Autowired private TaskScheduler gameScheduler;

    @Test
    void ordinaryGamesSchedulerDoesNotSerializeIndependentTasks() throws InterruptedException {
        var started = new CountDownLatch(3);
        var release = new CountDownLatch(1);
        var tasks = new ArrayList<java.util.concurrent.ScheduledFuture<?>>();
        try {
            for (int i = 0; i < 3; i++) tasks.add(gameScheduler.schedule(() -> {
                started.countDown();
                try { release.await(5, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }, Instant.now()));
            assertTrue(started.await(5, TimeUnit.SECONDS), "Обычные игры должны получать разные рабочие потоки");
        } finally {
            release.countDown();
            tasks.forEach(task -> task.cancel(true));
        }
    }

    @Test
    void separateSeriesRunInParallelAndCommitEveryDayExactlyOnce() throws InterruptedException {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            var config = new ExperimentConfig();
            config.setTarget(2);
            config.setMaxAttempts(2);
            config.setParallel(2);
            config.setMinBots(10);
            config.setMaxBots(10);
            config.setMinDays(30);
            config.setMaxDays(30);
            config.setSeed(1024 + i);
            config.setPolicyMix("CAUTIOUS,PRICE");
            ids.add(manager.create(config).getId());
        }

        boolean overlapped = false;
        Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
        while (Instant.now().isBefore(deadline)) {
            var attempts = ids.stream().flatMap(id -> runs.findBySeriesIdOrderByNumberDesc(id).stream()).toList();
            overlapped |= attempts.stream().filter(r -> "RUNNING".equals(r.getStatus())).count() >= 3;
            if (attempts.size() == 6 && attempts.stream().allMatch(r ->
                "COMPLETED".equals(r.getStatus()) || "INCOMPLETE".equals(r.getStatus()))) {
                assertTrue(overlapped, "Независимые серии должны выполнять игры одновременно");
                for (Long id : ids) assertEquals(List.of(2, 1),
                    runs.findBySeriesIdOrderByNumberDesc(id).stream().map(ExperimentRun::getNumber).toList());
                for (var attempt : attempts) {
                    var game = games.findById(attempt.getGame().getId()).orElseThrow();
                    assertEquals(GameStatus.FINISHED, game.getStatus());
                    assertEquals(30, game.getCurrentDay());
                    assertEquals(30, attempt.getCompletedDays());
                    assertEquals(300, statistics.countByManufacturer_Game_Id(game.getId()));
                }
                return;
            }
            Thread.sleep(100);
        }
        fail("Не завершились шесть игр: " + ids.stream()
            .map(id -> runs.findBySeriesIdOrderByNumberDesc(id).stream().map(ExperimentRun::getStatus).toList())
            .toList());
    }

    @Test
    void everyNewMarketRunsThroughRealTradingWithoutTechnicalErrors() throws InterruptedException {
        List<Long> ids = new ArrayList<>();
        for (String market : ExperimentScenarios.MARKETS.subList(3, 8)) {
            var config = new ExperimentConfig();
            config.setMarketMix(market);
            config.setTarget(1);
            config.setMaxAttempts(1);
            config.setParallel(1);
            config.setMinBots(2);
            config.setMaxBots(2);
            config.setMinDays(30);
            config.setMaxDays(30);
            config.setPolicyMix("CAUTIOUS,PRICE");
            ids.add(manager.create(config).getId());
        }

        Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
        while (Instant.now().isBefore(deadline)) {
            var attempts = ids.stream().flatMap(id -> runs.findBySeriesIdOrderByNumberDesc(id).stream()).toList();
            if (attempts.size() == ids.size() && attempts.stream().allMatch(r ->
                "COMPLETED".equals(r.getStatus()) || "INCOMPLETE".equals(r.getStatus()))) {
                for (var attempt : attempts) {
                    var game = games.findById(attempt.getGame().getId()).orElseThrow();
                    assertEquals(GameStatus.FINISHED, game.getStatus());
                    assertEquals(30, attempt.getCompletedDays());
                    assertEquals(60, statistics.countByManufacturer_Game_Id(game.getId()));
                }
                assertEquals(5, attempts.stream().map(ExperimentRun::getMarket).distinct().count());
                return;
            }
            Thread.sleep(100);
        }
        fail("Новые рынки не закончили торговлю: " + ids.stream()
            .map(id -> runs.findBySeriesIdOrderByNumberDesc(id).stream().map(ExperimentRun::getStatus).toList())
            .toList());
    }
}
