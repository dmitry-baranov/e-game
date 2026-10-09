package ru.itis.diploma.model;

import lombok.Data;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
public class PersonalScenario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Manufacturer manufacturer;
    private String name;
    private Integer productCount;
    private Integer capacity;
    private BigDecimal price;
    private BigDecimal quality;
    private Integer assortment;
    private Integer advertisingIntensity;
    private Integer advertisingDays;
    private BigDecimal businessCreditAmount;
    private LocalDateTime updatedAt;
}
