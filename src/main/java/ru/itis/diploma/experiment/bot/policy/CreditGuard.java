package ru.itis.diploma.experiment.bot.policy;

import org.springframework.stereotype.Component;
import ru.itis.diploma.game.dto.CommonProductionParameters;
import ru.itis.diploma.game.dto.StrategySnapshot;
import ru.itis.diploma.game.model.Game;
import ru.itis.diploma.game.model.Manufacturer;

import java.math.BigDecimal;

/** Financial admission rules applied after a policy proposes a normalized plan. */
@Component
public class CreditGuard {
    public boolean reject(String policyVersion, Manufacturer player, Game game,
                          CommonProductionParameters plan, StrategySnapshot view,
                          BigDecimal cost, BigDecimal shortfall) {
        if (shortfall.compareTo(game.getBaseCostPrice().multiply(BigDecimal.valueOf(80))) > 0) return true;
        if (!"bots-v6".equals(policyVersion) || player.getSelectedStrategy() == null) return false;
        return switch (player.getSelectedStrategy()) {
            case "DEBT_AVOID" -> shortfall.signum() > 0;
            case "CASH_RESERVE" -> player.getBalance().subtract(cost)
                .compareTo(player.getBalance().max(BigDecimal.ZERO).multiply(new BigDecimal("0.25"))) < 0;
            case "PAYMENT_AWARE" -> {
                int days = (plan.getProductCount() + player.getProductionCapacityPerDay() - 1)
                    / player.getProductionCapacityPerDay();
                BigDecimal reserve = view.payments().stream()
                    .filter(payment -> payment.day() > view.day() && payment.day() <= game.getCurrentDay() + days)
                    .map(StrategySnapshot.Payment::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                yield player.getBalance().subtract(cost).compareTo(reserve) < 0;
            }
            case "LIMITED_CREDIT_GROWTH" ->
                shortfall.compareTo(game.getBaseCostPrice().multiply(BigDecimal.valueOf(20))) > 0;
            default -> false;
        };
    }
}
