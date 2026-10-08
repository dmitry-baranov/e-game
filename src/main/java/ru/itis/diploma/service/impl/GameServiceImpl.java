package ru.itis.diploma.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.diploma.dto.CreateGameDto;
import ru.itis.diploma.dto.GameDto;
import ru.itis.diploma.exception.EntityNotFoundException;
import ru.itis.diploma.model.Game;
import ru.itis.diploma.model.GameResult;
import ru.itis.diploma.model.Manufacturer;
import ru.itis.diploma.model.enums.GameStatus;
import ru.itis.diploma.repository.GameRepository;
import ru.itis.diploma.repository.GameResultRepository;
import ru.itis.diploma.repository.ManufacturerRepository;
import ru.itis.diploma.service.AccountService;
import ru.itis.diploma.service.GameService;
import ru.itis.diploma.service.ManufacturerService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;


@Service
@RequiredArgsConstructor
public class GameServiceImpl implements GameService {

    private final AccountService accountService;
    private final GameRepository gameRepository;
    private final GameResultRepository gameResultRepository;
    private final ManufacturerService manufacturerService;
    private final ManufacturerRepository manufacturerRepository;

    @Override
    public Game getGameById(Long id) {
        return gameRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Игра не найдена"));
    }

    @Override
    @Transactional
    public void createGame(CreateGameDto gameDto) {
        createGameForExperiment(gameDto);
    }

    // The experiment owns its isolated database and its own admission control.
    public Game createGameForExperiment(CreateGameDto gameDto) {
        var newGame = Game.builder()
            .name(gameDto.getName())
            .timeUnit(gameDto.getTimeUnit())
            .startDate(LocalDateTime.now())
            .status(GameStatus.CREATED)
            .currentDay(0)
            .interestRateInvestmentCredit(gameDto.getInterestRateInvestmentCredit())
            .interestRateBusinessCredit(gameDto.getInterestRateBusinessCredit())
            .investmentCreditTermMonths(gameDto.getInvestmentCreditTermMonths())
            .salesTax(gameDto.getSalesTax())
            .baseCostPrice(gameDto.getBaseCostPrice())
            .baseAdvertisementPrice(gameDto.getBaseAdvertisementPrice())
            .productPower(gameDto.getProductPower())
            .assortmentWeight(gameDto.getAssortmentWeight())
            .advertisementWeight(gameDto.getAdvertisementWeight())
            .qualityWeight(gameDto.getQualityWeight())
            .purchaseLimit(gameDto.getPurchaseLimit())
            .habitWeight(gameDto.getHabitWeight())
            .dailySpendingLimit(gameDto.getDailySpendingLimit())
            .absoluteQualityProductLife(gameDto.getAbsoluteQualityProductLife())
            .habitTrackingDays(gameDto.getHabitTrackingDays())
            .build();
        gameRepository.save(newGame);
        createManufacturers(newGame, gameDto.getAccountIds());
        return newGame;
    }

    private void createManufacturers(Game newGame, List<Long> accountIds) {
        List<Manufacturer> manufacturers = accountIds.stream()
            .map(userId -> Manufacturer.builder()
                .game(newGame)
                .account(accountService.getById(userId))
                .investmentCreditDebt(BigDecimal.ZERO)
                .currentProductCount(0)
                .investmentCreditIsRepaid(false)
                .build())
            .toList();
        manufacturerRepository.saveAll(manufacturers);
    }

    @Override
    public List<GameDto> getAccountGames(Long accountId) {
        List<Manufacturer> accountManufacturers = manufacturerRepository.findByAccount_Id(accountId);
        return GameDto.from(gameRepository.findByIdIn(
            accountManufacturers.stream()
                .map(manufacturer -> manufacturer.getGame().getId())
                .toList()));
    }

    @Override
    public List<GameDto> getAllGames() {
        return GameDto.from(gameRepository.findAll());
    }

    @Override
    public Game save(Game game) {
        return gameRepository.save(game);
    }

    @Override
    @Transactional
    public void setStatus(Long gameId, GameStatus status) {
        Game game = gameRepository.lockById(gameId).orElseThrow();
        if (game.getStatus() != GameStatus.FINISHED) game.setStatus(status);
    }

    @Override
    @Transactional
    public void finishGame(Long id) {
        Game game = gameRepository.lockById(id).orElseThrow();
        if (game.getStatus() == GameStatus.FINISHED) return;
        game.setStatus(GameStatus.FINISHED);
        game.setEndDate(LocalDateTime.now());
        save(game);

        var manufacturers = manufacturerService.getGameManufacturers(game.getId());
        var gameResults = manufacturers.stream()
            .map(m -> GameResult.builder()
                .game(game)
                .manufacturer(m)
                .result(calculateFinishFinancialStatus(m, game))
                .build())
            .toList();
        gameResultRepository.saveAll(gameResults);
    }

    public List<GameResult> getGameResults(Game game) {
        return gameResultRepository.findByGameId(game.getId());
    }

    @Override
    public boolean existActiveGame() {
        return gameRepository.findFirstByStatusNot(GameStatus.FINISHED).isPresent();
    }

    private BigDecimal calculateFinishFinancialStatus(Manufacturer manufacturer, Game game) {
        var debts = manufacturerService.calculateManufacturerInvestmentCreditDebt(manufacturer, game)
            .add(manufacturerService.calculateManufacturerBusinessCreditDebt(manufacturer));
        return manufacturer.getBalance().subtract(debts);
    }

}
