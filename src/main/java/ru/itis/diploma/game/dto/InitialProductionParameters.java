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
/** Параметры самого первого производства, включая открытие мощности предприятия. */
public class InitialProductionParameters extends CommonProductionParameters {

    /** Рассчитанный кредит на первоначальный выпуск, рекламу и оборудование. */
    private BigDecimal investmentCreditAmount;

    /** Выбранная производственная мощность: максимум товаров за день. */
    private Integer productionCapacityPerDay;

}
