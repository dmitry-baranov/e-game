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
/** Факт перечисления налога с продаж конкретного предприятия. */
public class SalesTaxPayment {
    /** Идентификатор налогового платежа. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Уплаченная сумма налога. */
    private BigDecimal amount;

    /** Предприятие, перечислившее налог. */
    @ManyToOne
    private Manufacturer manufacturer;

    /** Игровой день списания налога. */
    private Integer date;
}
