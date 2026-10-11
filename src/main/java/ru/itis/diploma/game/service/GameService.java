package ru.itis.diploma.game.service;

import ru.itis.diploma.game.dto.CreateGameDto;
import ru.itis.diploma.game.dto.GameDto;
import ru.itis.diploma.game.model.Game;
import ru.itis.diploma.game.model.GameResult;
import ru.itis.diploma.game.model.enums.GameStatus;

import java.util.List;

public interface GameService {

    Game getGameById(Long id);

    void createGame(CreateGameDto gameDto);

    List<GameDto> getAccountGames(Long accountId);

    List<GameDto> getAllGames();

    Game save(Game game);

    void setStatus(Long gameId, GameStatus status);

    void finishGame(Long id);

    List<GameResult> getGameResults(Game game);

    boolean existActiveGame();
}
