package ru.itis.diploma.game.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@jakarta.persistence.Table(
    uniqueConstraints = @jakarta.persistence.UniqueConstraint(columnNames = {"manufacturer_id", "tradeDate"}),
    indexes = @jakarta.persistence.Index(columnList = "manufacturer_id,tradeDate"))
/** Сохранённые показатели одного предприятия за один торговый день. */
public class StatisticsInfo {

    /** Идентификатор дневного среза. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Номер игрового дня, к которому относятся показатели. */
    private Integer tradeDate;

    /** Предприятие, чьи результаты записаны. */
    @ManyToOne
    private Manufacturer manufacturer;

    /** Производственная мощность на этот день, штук в день. */
    private Integer productionCapacityPerDay;

    /** Число товаров, проданных за день. */
    private Integer productsSold;

    /** Цена единицы товара на этот день. */
    private BigDecimal price;

    /** Число товаров, произведённых за день. */
    private Integer productsProduced;

    /** Непроданный остаток товаров после дня. */
    private Integer productsInStock;

    /** Сумма налога с продаж, уплаченная в этот день. */
    private BigDecimal paidTaxesAmount;

    /** Сумма погашенного за день инвестиционного кредита. */
    private BigDecimal repaidInvestmentCreditAmount;
    /** Сумма погашенного за день оборотного кредита. */
    private BigDecimal repaidBusinessCreditAmount;

    /** Непогашенный инвестиционный долг на этот день. */
    private BigDecimal currentInvestmentCreditDebtAmount;
    /** Непогашенный оборотный долг на этот день. */
    private BigDecimal currentBusinessCreditDebtAmount;

    /** Денежный баланс предприятия после операций этого дня. */
    private BigDecimal balance;

}
