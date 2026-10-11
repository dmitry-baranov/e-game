package ru.itis.diploma.game;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.itis.diploma.game.dto.CreateGameDto;
import ru.itis.diploma.game.model.Game;
import ru.itis.diploma.game.service.TradingSessionService;
import ru.itis.diploma.game.service.impl.GameServiceImpl;

/**
 * Публичная граница игровой механики для автоматического проведения партий.
 * Сервис не знает о сериях, политиках ботов или отчётах: их оркестратор вызывает
 * те же создание игры, торговый день и финиш, что используют обычные игроки.
 * Транзакционные границы всего игрового дня остаются у вызывающего оркестратора.
 */
@Service
@RequiredArgsConstructor
public class GameSimulation {
    private final GameServiceImpl games;
    private final TradingSessionService trading;

    public Game create(CreateGameDto settings) { return games.createGameForParticipants(settings); }

    public void tradeDay(Game game) { trading.doDaysActivities(game); }

    public void finish(Game game) { games.finishGame(game.getId()); }
}
