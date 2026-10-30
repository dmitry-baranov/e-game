package ru.itis.diploma.game.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
/** Данные личного сценария, передаваемые рекомендательной системе игрока. */
public class StrategyPlan {
    /** ID сохранённого личного сценария. */
    private Long id;
    /** Название сценария. */
    private String name;
    /** Желаемый размер партии. */
    private Integer productCount;
    /** Желаемая производственная мощность в штуках за день. */
    private Integer capacity;
    /** Желаемая цена единицы товара. */
    private BigDecimal price;
    /** Желаемое качество товара. */
    private BigDecimal quality;
    /** Число разновидностей товара. */
    private Integer assortment;
    /** Интенсивность рекламной кампании. */
    private Integer advertisingIntensity;
    /** Длительность рекламы в днях. */
    private Integer advertisingDays;
    /** Планируемая сумма оборотного кредита. */
    private BigDecimal businessCreditAmount;
}
