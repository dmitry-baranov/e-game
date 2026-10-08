package ru.itis.diploma.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Manufacturer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Game game;

    @ManyToOne
    private Account account;

//    private BigDecimal income; //высчитывается в конце игры по результатам

    private BigDecimal balance;

    @Builder.Default
    private Integer currentProductCount = 0;

    private String selectedStrategy;

    // Reference values of the chosen course; never applied to production.
    private Integer selectedCourseProductCount;
    private BigDecimal selectedCoursePrice;
    private BigDecimal selectedCourseQuality;
    private Integer selectedCourseAssortment;
    private Integer selectedCourseAdvertisingIntensity;

    @Builder.Default
    private Boolean saveRecommendationHistory = false;

    @Builder.Default
    private Boolean allowStrategyTraining = false;

    private Integer productionCapacityPerDay;

    private BigDecimal investmentCreditAmount; //величина инвестиционного кредита(стартоый капитал)

    private BigDecimal investmentCreditDebt;

    private BigDecimal investmentCreditTermMonths;

    private boolean enteredInitialProductionParameters;

    private Boolean investmentCreditIsRepaid;

}
