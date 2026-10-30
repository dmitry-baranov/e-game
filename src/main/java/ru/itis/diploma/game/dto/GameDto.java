package ru.itis.diploma.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.itis.diploma.game.model.Game;
import ru.itis.diploma.game.model.enums.GameStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
/** Краткие сведения об игре для списка и страниц приложения. */
public class GameDto {

    /** ID игры. */
    private Long id;

    /** Отображаемое название игры. */
    private String name;

    /** Реальное время создания игры. */
    private LocalDateTime startDate;

    /** Реальное время завершения игры; до финиша null. */
    private LocalDateTime endDate;

    /** Текущее состояние игры. */
    private GameStatus status;

    /** Годовая ставка инвестиционного кредита в игре. */
    private BigDecimal interestRateInvestmentCredit;

    /** Годовая ставка оборотного кредита в игре. */
    private BigDecimal interestRateBusinessCredit;

    /** Процент налога с продаж. */
    private BigDecimal salesTax;

    /** Базовая себестоимость единицы товара. */
    private BigDecimal baseCostPrice;

    /** Базовая цена единицы рекламы. */
    private BigDecimal baseAdvertisementPrice;

    /** Цена приобретения дополнительной мощности. */
    private BigDecimal productPower;

    public static GameDto from(Game game) {
        return GameDto.builder()
            .id(game.getId())
            .name(game.getName())
            .startDate(game.getStartDate())
            .endDate(game.getEndDate())
            .status(game.getStatus())
            .interestRateInvestmentCredit(game.getInterestRateInvestmentCredit())
            .interestRateBusinessCredit(game.getInterestRateBusinessCredit())
            .salesTax(game.getSalesTax())
            .baseCostPrice(game.getBaseCostPrice())
            .baseAdvertisementPrice(game.getBaseAdvertisementPrice())
            .productPower(game.getProductPower())
            .build();
    }

    public static List<GameDto> from(List<Game> games) {
        return games.stream().map(GameDto::from).toList();
    }
}
