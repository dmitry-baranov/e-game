package ru.itis.diploma.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateGameDto {

    private String name;

    private Integer timeUnit;

    private BigDecimal interestRateInvestmentCredit;

    private BigDecimal interestRateBusinessCredit;

    private BigDecimal investmentCreditTermMonths;

    private BigDecimal salesTax;

    private BigDecimal baseCostPrice;

    private BigDecimal baseAdvertisementPrice;

    //Сумма средств, необходимых для производства еще одной единицы продукции
    private BigDecimal productPower; // цена создания единицы мощности производства (цена станка)

    private BigDecimal assortmentWeight;

    private BigDecimal qualityWeight;

    private BigDecimal advertisementWeight;

    private BigDecimal habitWeight;

    private Integer habitTrackingDays;

    private Integer purchaseLimit;

    private BigDecimal dailySpendingLimit;

    private Integer absoluteQualityProductLife;

    private List<Long> accountIds;

}
