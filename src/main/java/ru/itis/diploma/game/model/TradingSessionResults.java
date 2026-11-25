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

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
/** Факт продажи партии товаров одного производителя в торговый день. */
public class TradingSessionResults {

    /** Идентификатор записи о продаже. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Номер игрового дня продажи. */
    private Integer tradeDate;

    /** Предприятие-продавец. */
    @ManyToOne
    private Manufacturer manufacturer;

    /** Количество товаров в этой продаже. */
    private Integer productNumber;

    /** Цена одной единицы проданного товара. */
    private BigDecimal price;

    /** Качество товара на момент продажи; влияет на срок его службы. */
    private BigDecimal qualityIndex;

    /** Истёк ли срок службы купленного товара у покупателя. */
    private Boolean isWornOut;

}
