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
@jakarta.persistence.Table(indexes = @jakarta.persistence.Index(columnList = "manufacturer_id,startDate"))
/** Фактически принятый план одного производственного цикла, а не предложение бота. */
public class ProductionParameters {

    /** Идентификатор производственного цикла. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Номер дня с момента запуска игры
     */
    private Integer startDate;

    /** Последний торговый день цикла включительно. */
    private Integer endDate;

    /** ID принятого решения экспериментального бота; у человеческой игры null. */
    @jakarta.persistence.Column(name = "decision_log_id")
    private Long decisionLogId;

    /** Предприятие, запустившее этот цикл. */
    @ManyToOne
    private Manufacturer manufacturer;

    /**
     * Количество продукции, которое производитель будет выпускать при очередном выпуске
     */
    private Integer productCount;

    /**
     * Цена единицы продукции, выставляемая производителем
     */
    private BigDecimal price;

    /**
     * Рассчитанная себестоимость единицы продукции.
     */
    private BigDecimal costPrice;

    /**
     * Ассортимент (количество типов продукции)
     */
    private Integer assortment;

    /**
     * Величина оборотного кредита на сырье и рекламу - не вводится при старте игры производителем
     */
    private BigDecimal businessCreditAmount;


    /** Текущая ставка оборотного кредита этого цикла; при просрочке может расти. */
    private BigDecimal interestRateBusinessCredit;

    /**
     * Индекс качества продукции от 0 до 1.
     */
    private BigDecimal qualityIndex;

    /**
     * Мощность производства(ед/сутки) - максимум, который сможет выпускать
     */
    private Integer productionCapacityPerDay;

    /**
     * Сколько игровых дней потребуется на выпуск текущей партии.
     */
    private Integer timeToMarket;

}
