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
public class GameDto {

    private Long id;

    private String name;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    private GameStatus status;

    private BigDecimal interestRateInvestmentCredit;

    private BigDecimal interestRateBusinessCredit;

    private BigDecimal salesTax;

    private BigDecimal baseCostPrice;

    private BigDecimal baseAdvertisementPrice;

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
