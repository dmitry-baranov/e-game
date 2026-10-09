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
@jakarta.persistence.Table(indexes = @jakarta.persistence.Index(columnList = "manufacturer_id,startDate"))
public class ProductionParameters {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Номер дня с момента запуска игры
     */
    private Integer startDate;

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
     * Себестоимость единицы продукции, вычисляемое
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


    private BigDecimal interestRateBusinessCredit; //всегда берется базовая, если не получается выполнить списание по оборотному кредиту, то на остаток повышается процент

    /**
     * Индекс качества продукции(0-1) - валидировать на 0-1
     */
    private BigDecimal qualityIndex;

    /**
     * Мощность производства(ед/сутки) - максимум, который сможет выпускать
     */
    private Integer productionCapacityPerDay;

    /**
     * Количество дней выпуска продукции текущей партии
     */
    private Integer timeToMarket; // вычисляется по формуле

}
