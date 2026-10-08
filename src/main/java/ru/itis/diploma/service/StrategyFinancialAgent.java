package ru.itis.diploma.service;

import org.springframework.stereotype.Component;
import ru.itis.diploma.dto.StrategyAdvice;
import ru.itis.diploma.dto.StrategySnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class StrategyFinancialAgent {
    /** Recompute cash for model's cycle totals; costs and due payments stay deterministic. */
    public void modelCash(StrategySnapshot snapshot, StrategyAdvice.Card card) {
        if (card.getCredit().signum() > 0 || card.getRemaining() == null) return;
        BigDecimal due = snapshot.payments().stream()
            .filter(p -> p.day() <= snapshot.day() + card.getCycleDays())
            .map(StrategySnapshot.Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        card.setLowCash(modelCashAt(snapshot, card, due, card.getModelLowSales()));
        card.setNormalCash(modelCashAt(snapshot, card, due, card.getModelTypicalSales()));
        card.setHighCash(modelCashAt(snapshot, card, due, card.getModelHighSales()));
    }

    private BigDecimal modelCashAt(StrategySnapshot snapshot, StrategyAdvice.Card card, BigDecimal due, int units) {
        BigDecimal net = card.getPrice().multiply(BigDecimal.valueOf(units))
            .multiply(BigDecimal.ONE.subtract(snapshot.taxRate().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)));
        return card.getRemaining().subtract(due).add(net).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal cashForModelUnits(StrategySnapshot snapshot, StrategyAdvice.Card card, int units) {
        BigDecimal due = snapshot.payments().stream()
            .filter(p -> p.day() <= snapshot.day() + card.getCycleDays())
            .map(StrategySnapshot.Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return modelCashAt(snapshot, card, due, units);
    }
    public boolean evaluate(StrategySnapshot snapshot, StrategyAdvice.Card card, StrategyMarketAgent.Evidence market) {
        if (!snapshot.opened()) {
            card.setCredit(card.getCosts());
            card.setRiskLevel("Неопределённый");
            card.setRemaining(BigDecimal.ZERO);
            card.setReserve(null);
            card.setRisk(card.getRisk() + " Погашение стартового кредита зависит от будущих продаж.");
            return true;
        }
        BigDecimal newCredit = card.getCredit() == null ? BigDecimal.ZERO : card.getCredit();
        BigDecimal remaining = snapshot.balance().add(newCredit).subtract(card.getCosts());
        BigDecimal due = snapshot.payments().stream()
            .filter(p -> p.day() <= snapshot.day() + card.getCycleDays())
            .map(StrategySnapshot.Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reserve = due.multiply(new BigDecimal("1.25"))
            .add(card.getCosts().multiply(new BigDecimal("0.20"))).setScale(2, RoundingMode.HALF_UP);
        card.setRemaining(remaining);
        card.setReserve(reserve);
        if (remaining.signum() < 0 && !card.getKey().startsWith("PERSONAL:")) return false;
        if (remaining.signum() < 0 || newCredit.signum() > 0 || remaining.compareTo(due) < 0)
            card.setRiskLevel("Высокий");
        else if (remaining.compareTo(reserve) < 0 || market.observations() < 7 || market.stockLimited())
            card.setRiskLevel("Умеренный");
        else card.setRiskLevel("Низкий (относительно)");
        card.setRisk(card.getRisk() + (newCredit.signum() > 0 ?
            " Новый оборотный кредит: " + newCredit + "; будущий платёж по нему здесь не рассчитан." : "") +
            " Деньги после затрат до платежей: " + remaining +
            "; известные/оценочные платежи: " + due + "; рекомендованный резерв: " + reserve + ".");
        if (market.low() != null && newCredit.signum() == 0) {
            card.setLowCash(conditionalCash(snapshot, card, market.low(), remaining, due));
            card.setNormalCash(conditionalCash(snapshot, card, market.base(), remaining, due));
            card.setHighCash(conditionalCash(snapshot, card, market.high(), remaining, due));
        }
        return true;
    }

    private BigDecimal conditionalCash(StrategySnapshot snapshot, StrategyAdvice.Card card, int dailySales,
                                       BigDecimal remaining, BigDecimal due) {
        int units = Math.min(snapshot.stock() + card.getProductCount(), dailySales * card.getCycleDays());
        BigDecimal revenueAfterTax = card.getPrice().multiply(BigDecimal.valueOf(units))
            .multiply(BigDecimal.ONE.subtract(snapshot.taxRate().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)));
        return remaining.subtract(due).add(revenueAfterTax).setScale(2, RoundingMode.HALF_UP);
    }
}
