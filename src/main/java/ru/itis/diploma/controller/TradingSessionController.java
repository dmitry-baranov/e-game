package ru.itis.diploma.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import ru.itis.diploma.model.enums.GameStatus;
import ru.itis.diploma.service.GameService;
import ru.itis.diploma.service.TradingSessionService;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;

@Controller
@RequiredArgsConstructor
public class TradingSessionController {

    private final GameService gameService;
    private final TradingSessionService tradingSessionService;
    private final TaskScheduler taskScheduler;

    private final Map<Long, ScheduledFuture<?>> scheduledTasks = new HashMap<>();

    public synchronized boolean isRunning(Long gameId) {
        ScheduledFuture<?> task = scheduledTasks.get(gameId);
        return task != null && !task.isCancelled() && !task.isDone();
    }

    @GetMapping("game/{id}/trading-sessions/start/{timeUnit}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public synchronized String startTradingSessions(@PathVariable("id") Long gameId, @PathVariable long timeUnit) {
        if (!isRunning(gameId)) {
            var game = gameService.getGameById(gameId);
            if (game.getStatus() == GameStatus.FINISHED) return "redirect:/game/" + gameId;
            gameService.setStatus(gameId, GameStatus.STARTED);

            PeriodicTrigger trigger = new PeriodicTrigger(game.getTimeUnit(), TimeUnit.MINUTES);
            scheduledTasks.put(gameId, taskScheduler.schedule(() -> runGameDay(gameId), trigger));
        }
        return "redirect:/game/" + gameId;
    }

    @GetMapping("game/{id}/trading-sessions/stop")
    @PreAuthorize("hasAuthority('ADMIN')")
    public synchronized String stopTradingSessions(@PathVariable("id") Long gameId) {
        ScheduledFuture<?> task = scheduledTasks.remove(gameId);
        if (task != null) {
            task.cancel(false);
        }
        gameService.setStatus(gameId, GameStatus.STOPPED);
        return "redirect:/game/" + gameId;
    }

    private void runGameDay(Long gameId) {
        var game = gameService.getGameById(gameId);
        if (game.getStatus() == GameStatus.FINISHED) {
            synchronized (this) {
                var task = scheduledTasks.remove(gameId);
                if (task != null) task.cancel(false);
            }
            return;
        }
        if (game.getStatus() != GameStatus.STARTED) return;
        tradingSessionService.doDaysActivities(game);
    }

}
