package ru.itis.diploma.game.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Снимок доступных участнику данных перед решением текущего дня. Бот получает этот
 * объект, а не JPA-сущности и не скрытые настройки покупателя. Поля прошлого
 * производства могут быть {@code null}, если предприятие ещё не открыто.
 *
 * @param gameId игра, к которой относится снимок
 * @param day текущий игровой день
 * @param opened введены ли начальные параметры производства
 * @param balance текущие деньги предприятия; до открытия могут отсутствовать
 * @param stock число товаров на складе, не размер планируемой партии
 * @param baseCost базовая себестоимость единицы товара в игре до учёта качества
 * @param baseAdvertising базовая стоимость единицы рекламной интенсивности
 * @param powerCost стоимость увеличения производственной мощности
 * @param taxRate процент налога с продаж
 * @param investmentRate годовая процентная ставка инвестиционного кредита
 * @param businessRate годовая процентная ставка оборотного кредита
 * @param investmentTerm срок инвестиционного кредита в месяцах
 * @param capacity сколько товаров предприятие способно производить за день
 * @param productCount размер последней производственной партии, не остаток на складе
 * @param assortment число разновидностей товара в последнем плане
 * @param quality показатель качества последней партии
 * @param price цена единицы товара последней партии
 * @param advertisingIntensity интенсивность доступной текущей рекламной кампании
 * @param cycleDays продолжительность последнего производственного цикла в днях
 * @param sales собственные продажи и производство за последние семь торговых дней
 * @param competitors открытые для участника предложения других производителей
 * @param payments предстоящие платежи по кредитам, известные предприятию
 * @param selectedStrategy сохранённый код выбранного курса; у бота — имя его политики
 */
public record StrategySnapshot(long gameId, int day, boolean opened, BigDecimal balance,
                               int stock, BigDecimal baseCost, BigDecimal baseAdvertising,
                               BigDecimal powerCost, BigDecimal taxRate, BigDecimal investmentRate,
                               BigDecimal businessRate, Integer investmentTerm,
                               Integer capacity, Integer productCount, Integer assortment,
                               BigDecimal quality, BigDecimal price, Integer advertisingIntensity,
                               int cycleDays, List<Sale> sales, List<Competitor> competitors,
                               List<Payment> payments, String selectedStrategy) {
    // FreeMarker's JavaBean wrapper requires getters rather than record component accessors.
    public int getDay() { return day; }
    public int getStock() { return stock; }
    public boolean isOpened() { return opened; }
    public Integer getCapacity() { return capacity; }
    public List<Sale> getSales() { return sales; }
    public List<Payment> getPayments() { return payments; }

    /**
     * Собственные результаты одного торгового дня.
     * @param day номер дня
     * @param sold число проданных товаров
     * @param produced число произведённых товаров
     * @param stock остаток на складе после торгового дня
     */
    public record Sale(int day, int sold, int produced, int stock) {
        public int getDay() { return day; }
        public int getSold() { return sold; }
        public int getProduced() { return produced; }
        public int getStock() { return stock; }
    }
    /**
     * Предстоящий платёж, учитываемый при оценке доступных средств.
     * @param day день ожидаемого списания
     * @param amount ожидаемая сумма платежа
     * @param label понятное название вида кредита
     */
    public record Payment(int day, BigDecimal amount, String label) {
        public int getDay() { return day; }
        public BigDecimal getAmount() { return amount; }
        public String getLabel() { return label; }
    }
    /**
     * Видимые параметры последнего открытого предложения другого производителя.
     * @param price цена единицы его товара
     * @param quality качество его товара
     * @param assortment число разновидностей товара
     * @param advertisingIntensity интенсивность его рекламы
     */
    public record Competitor(BigDecimal price, BigDecimal quality, Integer assortment, Integer advertisingIntensity) {}
}
