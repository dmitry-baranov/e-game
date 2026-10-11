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
public class ManufacturerFinancialStatus {

    private Integer day;

    private BigDecimal balance;

    private BigDecimal investmentCreditDebt;

    private BigDecimal businessCreditDebt;

    private BigDecimal todayRevenue;

    private Integer timeToMarketEndDay;

    private boolean activeManufacturingCycle;

}
