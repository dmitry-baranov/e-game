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
public class CommonProductionParameters {

    private Integer productCount;

    private BigDecimal price;//покупатель

    private BigDecimal costPrice;

    private Integer assortment; //покупатель

    private BigDecimal qualityIndex; //покупатель

    private Integer advertisingDays;

    /**
     * Количество источников рекламы(каналы распространения рекламы - радио, тв, интернет), ограничение до 7
     */
    private Integer advertisingIntensityIndex; //покупатель

    private BigDecimal advertisingCost;

    private Integer timeToMarket;
}
