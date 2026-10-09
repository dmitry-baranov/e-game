package ru.itis.diploma.model;

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
 * Затраты на рекламу производителей в течение игры
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Advertisement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Manufacturer manufacturer;

    private Integer startDate;

    private Integer endDate;

    private BigDecimal cost;

    private Integer intensityIndex; // индекс интенсивности рекламы(условно количество источников рекламы, будет вводить производитель на основе теор справки)
}
