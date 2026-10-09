package ru.itis.diploma.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.itis.diploma.model.enums.GameStatus;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Game {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private LocalDateTime startDate;

    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    private GameStatus status;

    @Builder.Default
    private Integer currentDay = 0;

    private Integer timeUnit; // модельное время в минутах(сутки = ? минут)

    private BigDecimal interestRateInvestmentCredit; // процентная ставка на стартовый капитал(инвестиционный кредит - для организации производства(оборудование, помещение))

    private BigDecimal investmentCreditTermMonths; // cрок инвестиционного кредита в месяцах

    private BigDecimal interestRateBusinessCredit; // процентная ставка на оборотные средства(оборотный кредит - на сырье для выпуска продукции)

    private BigDecimal salesTax; // налог с продаж

    private BigDecimal baseCostPrice;//базовая себестоимость производства единицы продукции

    private BigDecimal baseAdvertisementPrice; //базовая цена рекламы

    private BigDecimal productPower; //сколько необходимо денег для того чтобы производить на единицу продукции больше

    private BigDecimal assortmentWeight;

    private BigDecimal qualityWeight;

    private BigDecimal advertisementWeight;

    private BigDecimal habitWeight;

    private Integer habitTrackingDays;

    private Integer purchaseLimit; // величина насыщения покупателя(по наличии такого количества товаров покупатель перестает совершать покупки до тех пор, пока не появилась необходимость купить новые товары по причине изношенности имеющихся)

    private BigDecimal dailySpendingLimit; // сумме средств покупателя в день, которые он может потратить на покупки, максимум ежедневных трат

    private Integer absoluteQualityProductLife; // срок эксплуатации для абсолютно качетственного товара(индекс кач-ва = 1)

}
