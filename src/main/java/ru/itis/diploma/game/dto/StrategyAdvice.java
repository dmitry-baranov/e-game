package ru.itis.diploma.game.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
/** Ответ игроку с карточками советов; бот после упрощения не использует эти карточки. */
public class StrategyAdvice {
    /** Игровой день, на который рассчитаны рекомендации. */
    private int gameDay;
    /** Время создания ответа. */
    private LocalDateTime generatedAt;
    @JsonIgnore
    public String getGeneratedAtText() { return generatedAt == null ? "" : generatedAt.toString(); }
    /** Курс предприятия, выбранный до получения новых советов. */
    private String selectedStrategy;
    /** Пояснение о несовпадении выбранного курса и текущего состояния. */
    private String courseMismatch;
    /** Необязательное текстовое объяснение рекомендаций от ИИ. */
    private String aiExplanation;
    /** Диагностика причины неприменимости модели, не прогноз и не пояснение ИИ. */
    @JsonIgnore
    private String modelFallbackReason;
    /** ID сохранённого ответа в истории рекомендаций; может отсутствовать. */
    private Long historyId;
    /** Варианты плана, предложенные игроку. */
    private List<Card> recommendations;
    /** Мнения отдельных обученных моделей о предлагаемом действии. */
    private List<ModelOpinion> modelOpinions;

    /** Краткое мнение отдельной модели о варианте действий. */
    @Data
    public static class ModelOpinion {
        /** Название модели. */
        private String model;
        /** Версия модели. */
        private String version;
        /** Предлагаемое действие. */
        private String action;
        /** Объяснение предпочтения действия. */
        private String rationale;
    }

    /** Один вариант рекомендованного плана и связанные с ним оценки. */
    @Data
    public static class Card {
        /** Машинный ключ курса, используемый при выборе карточки. */
        private String key;
        /** Название карточки для игрока. */
        private String name;
        /** Причина рекомендации. */
        private String reason;
        /** Описание риска. */
        private String risk;
        /** Категория уровня риска. */
        private String riskLevel;
        /** Предположение, на котором основана оценка. */
        private String assumption;
        /** Источник карточки: правила или обученная модель. */
        private String source = "RULES";
        /** Название модели при модельной рекомендации. */
        private String modelName;
        /** Версия модели при модельной рекомендации. */
        private String modelVersion;
        /** Пессимистичный прогноз продаж модели, штук. */
        private Integer modelLowSales;
        /** Типичный прогноз продаж модели, штук. */
        private Integer modelTypicalSales;
        /** Оптимистичный прогноз продаж модели, штук. */
        private Integer modelHighSales;
        /** Предложенный размер партии. */
        private Integer productCount;
        /** Предложенная мощность в штуках за день. */
        private Integer capacity;
        /** Предложенная цена единицы товара. */
        private BigDecimal price;
        /** Предложенный индекс качества. */
        private BigDecimal quality;
        /** Предложенное число разновидностей товара. */
        private Integer assortment;
        /** Предложенная интенсивность рекламы. */
        private Integer advertisingIntensity;
        /** Предложенное число дней рекламы. */
        private Integer advertisingDays;
        /** Рассчитанная продолжительность производственного цикла. */
        private Integer cycleDays;
        /** Оценка всех затрат на производство и рекламу. */
        private BigDecimal costs;
        /** Оценка необходимого кредита. */
        private BigDecimal credit;
        /** Ожидаемый остаток денег после расходов. */
        private BigDecimal remaining;
        /** Денежный резерв, учитываемый при оценке риска. */
        private BigDecimal reserve;
        /** Число наблюдений, использованных для оценки. */
        private Integer observations;
        /** Пессимистичная оценка продаж, штук. */
        private Integer lowSales;
        /** Обычная оценка продаж, штук. */
        private Integer normalSales;
        /** Оптимистичная оценка продаж, штук. */
        private Integer highSales;
        /** Прогноз денег в пессимистичном сценарии. */
        private BigDecimal lowCash;
        /** Прогноз денег в обычном сценарии. */
        private BigDecimal normalCash;
        /** Прогноз денег в оптимистичном сценарии. */
        private BigDecimal highCash;
    }
}
