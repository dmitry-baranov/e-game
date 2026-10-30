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
@jakarta.persistence.Table(uniqueConstraints =
    @jakarta.persistence.UniqueConstraint(columnNames = {"game_id", "account_id"}))
/** Предприятие конкретного аккаунта в конкретной игре: баланс и склад изолированы по играм. */
public class Manufacturer {

    /** Идентификатор предприятия в БД. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Игра, внутри которой существует предприятие. */
    @ManyToOne
    private Game game;

    /** Аккаунт владельца; один аккаунт может участвовать в разных играх. */
    @ManyToOne
    private Account account;

    /** Доступные предприятию деньги в текущей игре. */
    private BigDecimal balance;

    /** Текущий остаток непроданных товаров на складе. */
    @Builder.Default
    private Integer currentProductCount = 0;

    /** Выбранный курс игрока; у бота — код политики CAUTIOUS, PRICE или STOCK. */
    private String selectedStrategy;

    // Параметры выбранного человеком курса для сравнения; сами по себе производство не открывают.
    /** Рекомендованный размер партии выбранного курса. */
    private Integer selectedCourseProductCount;
    /** Рекомендованная цена выбранного курса. */
    private BigDecimal selectedCoursePrice;
    /** Рекомендованное качество выбранного курса. */
    private BigDecimal selectedCourseQuality;
    /** Рекомендованный ассортимент выбранного курса. */
    private Integer selectedCourseAssortment;
    /** Рекомендованная рекламная интенсивность выбранного курса. */
    private Integer selectedCourseAdvertisingIntensity;

    /** Разрешено ли сохранять историю персональных рекомендаций владельца. */
    @Builder.Default
    private Boolean saveRecommendationHistory = false;

    /** Согласие на сбор собственных эпизодов для обучения; у COLLECT-ботов включено. */
    @Builder.Default
    private Boolean allowStrategyTraining = false;

    /** Производственная мощность предприятия в единицах товара за день. */
    private Integer productionCapacityPerDay;

    /** Первоначально выданный инвестиционный кредит на открытие производства. */
    private BigDecimal investmentCreditAmount;

    /** Невыплаченная часть инвестиционного кредита. */
    private BigDecimal investmentCreditDebt;

    /** Срок инвестиционного кредита предприятия в месяцах. */
    private BigDecimal investmentCreditTermMonths;

    /** Установлены ли первые параметры производства (открыто ли предприятие). */
    private boolean enteredInitialProductionParameters;

    /** Полностью ли выплачен инвестиционный кредит. */
    private Boolean investmentCreditIsRepaid;

}
