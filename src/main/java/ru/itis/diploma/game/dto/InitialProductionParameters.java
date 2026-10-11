package ru.itis.diploma.game.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class InitialProductionParameters extends CommonProductionParameters {

    private BigDecimal investmentCreditAmount;

    private Integer productionCapacityPerDay;

}
