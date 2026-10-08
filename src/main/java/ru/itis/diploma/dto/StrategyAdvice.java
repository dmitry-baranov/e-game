package ru.itis.diploma.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class StrategyAdvice {
    private int gameDay;
    private LocalDateTime generatedAt;
    @JsonIgnore
    public String getGeneratedAtText() { return generatedAt == null ? "" : generatedAt.toString(); }
    private String selectedStrategy;
    private String courseMismatch;
    private String aiExplanation;
    private Long historyId;
    private List<Card> recommendations;
    private List<ModelOpinion> modelOpinions;

    @Data
    public static class ModelOpinion {
        private String model;
        private String version;
        private String action;
        private String rationale;
    }

    @Data
    public static class Card {
        private String key;
        private String name;
        private String reason;
        private String risk;
        private String riskLevel;
        private String assumption;
        private String source = "RULES";
        private String modelName;
        private String modelVersion;
        private Integer modelLowSales;
        private Integer modelTypicalSales;
        private Integer modelHighSales;
        private Integer productCount;
        private Integer capacity;
        private BigDecimal price;
        private BigDecimal quality;
        private Integer assortment;
        private Integer advertisingIntensity;
        private Integer advertisingDays;
        private Integer cycleDays;
        private BigDecimal costs;
        private BigDecimal credit;
        private BigDecimal remaining;
        private BigDecimal reserve;
        private Integer observations;
        private Integer lowSales;
        private Integer normalSales;
        private Integer highSales;
        private BigDecimal lowCash;
        private BigDecimal normalCash;
        private BigDecimal highCash;
    }
}
