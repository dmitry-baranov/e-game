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
/** Общие параметры предлагаемого производства; вычисляемые поля заполняет игровой сервис. */
public class CommonProductionParameters {

    /** Размер партии в единицах товара, а не текущий остаток на складе. */
    private Integer productCount;

    /** Цена единицы товара для покупателя. */
    private BigDecimal price;

    /** Рассчитанная себестоимость единицы товара; обычно заполняется сервисом. */
    private BigDecimal costPrice;

    /** Число разновидностей товара в партии. */
    private Integer assortment;

    /** Индекс качества товара от 0 до 1. */
    private BigDecimal qualityIndex;

    /** Сколько дней показывать рекламу этого выпуска. */
    private Integer advertisingDays;

    /** Индекс интенсивности рекламы (число условных каналов). */
    private Integer advertisingIntensityIndex;

    /** Рассчитанная стоимость рекламной кампании. */
    private BigDecimal advertisingCost;

    /** Рассчитанное число дней до выпуска партии. */
    private Integer timeToMarket;
}
