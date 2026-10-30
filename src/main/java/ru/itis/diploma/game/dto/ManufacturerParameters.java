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
/** Сводные параметры предприятия, производства и рекламы для отображения игроку. */
public class ManufacturerParameters {

    /** ID предприятия в этой игре. */
    private Long id;

    /** Отображаемое имя владельца. */
    private String fullName;

    /** День начала актуального производственного цикла. */
    private Integer startDate;

    /** Доступный баланс предприятия. */
    private BigDecimal balance;

    /** Первоначально выданный инвестиционный кредит. */
    private BigDecimal investmentCreditAmount;

    /** Оборотный кредит актуального цикла. */
    private BigDecimal businessCreditAmount;

    /** Ставка оборотного кредита актуального цикла. */
    private BigDecimal interestRateBusinessCredit;

    /** Размер партии актуального производства. */
    private Integer productCount;

    /** Цена единицы выпускаемого товара. */
    private BigDecimal price;

    /** Себестоимость единицы товара. */
    private BigDecimal costPrice;

    /** Число разновидностей товара. */
    private Integer assortment;

    /** Индекс качества товара. */
    private BigDecimal qualityIndex;

    /** Производственная мощность, штук в день. */
    private Integer productionCapacityPerDay;

    /** Продолжительность выпуска партии в игровых днях. */
    private Integer timeToMarket;

    /** Первый день рекламной кампании. */
    private Integer advertisingStartDate;

    /** Последний день рекламной кампании. */
    private Integer advertisingEndDate;

    /** Стоимость рекламной кампании. */
    private BigDecimal advertisingCost;

    /** Интенсивность рекламной кампании. */
    private Integer advertisingIntensityIndex;

}
