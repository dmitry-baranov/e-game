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
/** Параметры следующего выпуска уже открытого предприятия. */
public class NewProductionParameters extends CommonProductionParameters {

    /** Рассчитанные затраты на всю партию и рекламу. */
    private BigDecimal productionCosts;
    /** Запрашиваемый оборотный кредит для покрытия нехватки денег. */
    private BigDecimal businessCreditAmount;

}
