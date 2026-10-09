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

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class TradingSessionResults {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer tradeDate;

    @ManyToOne
    private Manufacturer manufacturer;

    private Integer productNumber; //количество проданных товаров

    private BigDecimal price; //цена товара

    private BigDecimal qualityIndex;

    private Boolean isWornOut; // изношенный товар

}
