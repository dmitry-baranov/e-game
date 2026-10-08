package ru.itis.diploma.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class StatisticsInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer tradeDate;

    @ManyToOne
    private Manufacturer manufacturer;

    private Integer productionCapacityPerDay; //производственные мощности

    private Integer productsSold; //количество проданного товаров

    private BigDecimal price; //цена товара

    private Integer productsProduced; //количество произведенного товаров

    private Integer productsInStock;

    private BigDecimal paidTaxesAmount; //выплаченные налоги

    private BigDecimal repaidInvestmentCreditAmount; //выплаченные кредиты
    private BigDecimal repaidBusinessCreditAmount; //выплаченные кредиты

    private BigDecimal currentInvestmentCreditDebtAmount; //текущий долг
    private BigDecimal currentBusinessCreditDebtAmount; //текущий долг

    private BigDecimal balance;

}
