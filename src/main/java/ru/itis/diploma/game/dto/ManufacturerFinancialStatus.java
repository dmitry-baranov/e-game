package ru.itis.diploma.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
/** Финансовое положение предприятия на текущий день игры. */
public class ManufacturerFinancialStatus {

    /** Номер текущего игрового дня. */
    private Integer day;

    /** Доступные деньги предприятия. */
    private BigDecimal balance;

    /** Остаток задолженности по инвестиционному кредиту. */
    private BigDecimal investmentCreditDebt;

    /** Остаток задолженности по оборотным кредитам. */
    private BigDecimal businessCreditDebt;

    /** Выручка от продаж за сегодняшний день. */
    private BigDecimal todayRevenue;

    /** День завершения текущего производственного цикла; если его нет, может быть null. */
    private Integer timeToMarketEndDay;

    /** Идёт ли сейчас выпуск очередной партии. */
    private boolean activeManufacturingCycle;

}
