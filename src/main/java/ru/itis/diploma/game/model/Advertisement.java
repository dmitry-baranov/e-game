package ru.itis.diploma.game.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;

/**
 * Одна рекламная кампания предприятия и её стоимость в игре.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Advertisement {
    /** Идентификатор кампании. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Предприятие, оплатившее рекламу. */
    @ManyToOne
    private Manufacturer manufacturer;

    /** Первый день показа рекламы. */
    private Integer startDate;

    /** Последний день показа рекламы включительно. */
    private Integer endDate;

    /** Общая стоимость кампании. */
    private BigDecimal cost;

    /** Выбранный индекс интенсивности/число условных рекламных каналов. */
    private Integer intensityIndex;
}
