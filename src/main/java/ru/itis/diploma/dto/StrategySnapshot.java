package ru.itis.diploma.dto;

import java.math.BigDecimal;
import java.util.List;

/** Only data available on the manufacturer's pages. Never pass JPA entities to an agent. */
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

    public record Sale(int day, int sold, int produced, int stock) {
        public int getDay() { return day; }
        public int getSold() { return sold; }
        public int getProduced() { return produced; }
        public int getStock() { return stock; }
    }
    public record Payment(int day, BigDecimal amount, String label) {
        public int getDay() { return day; }
        public BigDecimal getAmount() { return amount; }
        public String getLabel() { return label; }
    }
    public record Competitor(BigDecimal price, BigDecimal quality, Integer assortment, Integer advertisingIntensity) {}
}
