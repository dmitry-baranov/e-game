package ru.itis.diploma.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ManufacturerParameters {

    private Long id;

    private String fullName;

    private Integer startDate;

    private BigDecimal balance;

    private BigDecimal investmentCreditAmount;

    private BigDecimal businessCreditAmount;

    private BigDecimal interestRateBusinessCredit;

    private Integer productCount;

    private BigDecimal price;

    private BigDecimal costPrice;

    private Integer assortment;

    private BigDecimal qualityIndex;

    private Integer productionCapacityPerDay;

    private Integer timeToMarket;

    private Integer advertisingStartDate;

    private Integer advertisingEndDate;

    private BigDecimal advertisingCost;

    private Integer advertisingIntensityIndex;

}
