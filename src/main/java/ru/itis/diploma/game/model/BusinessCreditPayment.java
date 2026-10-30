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
/** Платёж по оборотному кредиту конкретного производственного цикла. */
public class BusinessCreditPayment {

    /** Идентификатор платежа. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Цикл, ради которого был взят оборотный кредит. */
    @ManyToOne
    private ProductionParameters productionParameters;

    /** День фактического платежа; для первой записи может отсутствовать. */
    private Integer date;

    /** День, когда ожидается следующая выплата. */
    private Integer nextDate;

    /** Сумма, уплаченная в этот день. */
    private BigDecimal amount;

    /** Сумма следующего платежа с процентами. */
    private BigDecimal nextAmount;

}
