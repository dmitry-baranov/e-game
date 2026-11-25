package ru.itis.diploma.game.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.itis.diploma.game.model.enums.GameStatus;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
/** Условия одной игры, общие для всех участвующих в ней производителей. */
public class Game {

    /** Идентификатор игры. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Отображаемое название игры. */
    private String name;

    /** Реальное время создания игры. */
    private LocalDateTime startDate;

    /** Реальное время окончания; до завершения null. */
    private LocalDateTime endDate;

    /** CREATED, STARTED, STOPPED или FINISHED. */
    @Enumerated(EnumType.STRING)
    private GameStatus status;

    /** Номер уже достигнутого игрового дня, изначально ноль. */
    @Builder.Default
    private Integer currentDay = 0;

    /** Длительность одного модельного дня в минутах для обычного расписания игры. */
    private Integer timeUnit;

    /** Годовая ставка инвестиционного кредита, в процентах. */
    private BigDecimal interestRateInvestmentCredit;

    /** Срок погашения инвестиционного кредита в месяцах. */
    private BigDecimal investmentCreditTermMonths;

    /** Годовая ставка оборотного кредита на следующую партию, в процентах. */
    private BigDecimal interestRateBusinessCredit;

    /** Ставка налога с продаж, в процентах. */
    private BigDecimal salesTax;

    /** Базовая себестоимость одной единицы товара до учёта качества. */
    private BigDecimal baseCostPrice;

    /** Базовая стоимость единицы рекламной интенсивности в день. */
    private BigDecimal baseAdvertisementPrice;

    /** Стоимость приобретения дополнительной производственной мощности. */
    private BigDecimal productPower;

    /** Вес ассортимента при выборе товара виртуальным покупателем. */
    private BigDecimal assortmentWeight;

    /** Вес качества в предпочтениях виртуального покупателя. */
    private BigDecimal qualityWeight;

    /** Вес рекламы в предпочтениях виртуального покупателя. */
    private BigDecimal advertisementWeight;

    /** Вес привычки покупать у знакомого производителя. */
    private BigDecimal habitWeight;

    /** Сколько прошедших дней учитывается при оценке привычки покупателя. */
    private Integer habitTrackingDays;

    /** Максимум ещё не изношенных товаров у покупателя одновременно. */
    private Integer purchaseLimit;

    /** Максимальный бюджет покупателя на один торговый день. */
    private BigDecimal dailySpendingLimit;

    /** Срок службы товара с максимальным качеством (индекс 1), в днях. */
    private Integer absoluteQualityProductLife;

}
