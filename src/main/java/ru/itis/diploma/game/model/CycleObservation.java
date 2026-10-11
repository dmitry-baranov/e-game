package ru.itis.diploma.game.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Materialized facts for one production cycle, updated with the trading day transaction. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "production_parameters_id"),
    indexes = @Index(columnList = "manufacturer_id,complete,endDate"))
@Getter
@Setter
public class CycleObservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne(optional = false)
    private ProductionParameters productionParameters;
    @ManyToOne(optional = false)
    private Manufacturer manufacturer;
    private int startDate;
    private int endDate;
    private int expectedDays;
    private int observedDays;
    private boolean complete;
    private int productsSold;
    private int productsProduced;
    private BigDecimal revenue = BigDecimal.ZERO;
    private BigDecimal payments = BigDecimal.ZERO;
    private Integer endingStock;
    private BigDecimal balanceChange;
    private int advertisingIntensity;
    private int advertisingDays;
}
