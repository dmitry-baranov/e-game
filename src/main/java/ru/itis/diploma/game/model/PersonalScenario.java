package ru.itis.diploma.game.model;

import lombok.Data;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Сохранённый игроком собственный вариант производственного плана. */
@Data
@Entity
public class PersonalScenario {
    /** Идентификатор сценария. */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Предприятие автора сценария. */
    @ManyToOne(optional = false)
    private Manufacturer manufacturer;
    /** Название варианта, заданное игроком. */
    private String name;
    /** Планируемый размер партии. */
    private Integer productCount;
    /** Планируемая производственная мощность в штуках за день. */
    private Integer capacity;
    /** Цена единицы товара. */
    private BigDecimal price;
    /** Выбранный индекс качества товара. */
    private BigDecimal quality;
    /** Число разновидностей товара. */
    private Integer assortment;
    /** Интенсивность рекламы. */
    private Integer advertisingIntensity;
    /** Продолжительность рекламы в игровых днях. */
    private Integer advertisingDays;
    /** Запланированный оборотный кредит. */
    private BigDecimal businessCreditAmount;
    /** Время последнего изменения сценария. */
    private LocalDateTime updatedAt;
}
