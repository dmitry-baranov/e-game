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
/** Входные настройки новой игры; эксперимент передаёт сюда также аккаунты ботов. */
public class CreateGameDto {

    /** Отображаемое название игры. */
    private String name;

    /** Длительность модельного дня в минутах для обычной игры. */
    private Integer timeUnit;

    /** Годовая ставка инвестиционного кредита, в процентах. */
    private BigDecimal interestRateInvestmentCredit;

    /** Годовая ставка оборотного кредита, в процентах. */
    private BigDecimal interestRateBusinessCredit;

    /** Срок инвестиционного кредита в месяцах. */
    private BigDecimal investmentCreditTermMonths;

    /** Ставка налога с продаж, в процентах. */
    private BigDecimal salesTax;

    /** Базовая себестоимость единицы товара до учёта качества. */
    private BigDecimal baseCostPrice;

    /** Базовая стоимость единицы рекламной интенсивности. */
    private BigDecimal baseAdvertisementPrice;

    /** Стоимость приобретения дополнительной производственной мощности. */
    private BigDecimal productPower;

    /** Вес ассортимента в предпочтениях виртуального покупателя. */
    private BigDecimal assortmentWeight;

    /** Вес качества в предпочтениях виртуального покупателя. */
    private BigDecimal qualityWeight;

    /** Вес рекламы в предпочтениях виртуального покупателя. */
    private BigDecimal advertisementWeight;

    /** Вес привычки покупателя к знакомому производителю. */
    private BigDecimal habitWeight;

    /** Число дней истории покупок для оценки привычки. */
    private Integer habitTrackingDays;

    /** Максимум ещё не изношенных товаров, которыми располагает покупатель. */
    private Integer purchaseLimit;

    /** Максимальный бюджет покупателя на один день. */
    private BigDecimal dailySpendingLimit;

    /** Срок службы товара максимального качества в днях. */
    private Integer absoluteQualityProductLife;

    /** ID аккаунтов участников, для которых создаются предприятия в этой игре. */
    private List<Long> accountIds;

}
