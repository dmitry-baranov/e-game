package ru.itis.diploma.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class StrategyPlan {
    private Long id;
    private String name;
    private Integer productCount;
    private Integer capacity;
    private BigDecimal price;
    private BigDecimal quality;
    private Integer assortment;
    private Integer advertisingIntensity;
    private Integer advertisingDays;
    private BigDecimal businessCreditAmount;
}
