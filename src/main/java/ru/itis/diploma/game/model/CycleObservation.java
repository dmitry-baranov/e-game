package ru.itis.diploma.game.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Итоги одного производственного цикла, обновляемые вместе с торговым днём. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "production_parameters_id"),
    indexes = @Index(columnList = "manufacturer_id,complete,endDate"))
@Getter
@Setter
public class CycleObservation {
    /** Идентификатор наблюдения. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Производственный цикл, по которому собираются итоги. */
    @OneToOne(optional = false)
    private ProductionParameters productionParameters;
    /** Предприятие, которому принадлежит цикл. */
    @ManyToOne(optional = false)
    private Manufacturer manufacturer;
    /** День начала производственного цикла. */
    private int startDate;
    /** Последний день цикла включительно. */
    private int endDate;
    /** Сколько торговых дней должно попасть в наблюдение. */
    private int expectedDays;
    /** Сколько торговых дней фактически записано. */
    private int observedDays;
    /** Есть ли статистика за каждый ожидаемый день цикла. */
    private boolean complete;
    /** Сумма продаж за наблюдаемые дни. */
    private int productsSold;
    /** Сумма произведённых товаров за наблюдаемые дни. */
    private int productsProduced;
    /** Расчётная выручка от продаж наблюдаемых товаров. */
    private BigDecimal revenue = BigDecimal.ZERO;
    /** Налоги и кредитные платежи за наблюдаемые дни. */
    private BigDecimal payments = BigDecimal.ZERO;
    /** Остаток на складе в конце цикла; null, если итогового дня ещё нет. */
    private Integer endingStock;
    /** Разность баланса после и до цикла; null, если одного среза нет. */
    private BigDecimal balanceChange;
    /** Интенсивность рекламы, связанной с этим циклом. */
    private int advertisingIntensity;
    /** Число дней рекламной кампании этого цикла. */
    private int advertisingDays;
}
