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
/** Факт платежа по инвестиционному кредиту и дата следующего платежа. */
public class InvestmentCreditPayment {
    /** Идентификатор платежа. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Погашенная часть основного долга. */
    private BigDecimal principalPayment;

    /** Уплаченные проценты по кредиту. */
    private BigDecimal interestAmount;

    /** Предприятие-заёмщик. */
    @ManyToOne
    private Manufacturer manufacturer;

    /** День внесения текущего платежа. */
    private Integer date;

    /** Назначенный день следующего платежа. */
    private Integer nextDate;
}
