package ru.itis.diploma.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import ru.itis.diploma.model.Account;
import ru.itis.diploma.security.details.AccountUserDetails;

@Controller @RequiredArgsConstructor
@ConditionalOnProperty(name = "experiment.enabled", havingValue = "false", matchIfMissing = true)
public class GameAnalysisController {
    private final JdbcTemplate jdbc;

    @GetMapping("/game/{id}/analysis") @PreAuthorize("isAuthenticated()")
    public String analysis(@PathVariable Long id, @AuthenticationPrincipal AccountUserDetails user, Model model) {
        var games = jdbc.queryForList("SELECT id, name, status, start_date, end_date, current_day FROM game WHERE id=? AND status='FINISHED'", id);
        if (games.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var own = jdbc.queryForList("SELECT id, balance, investment_credit_amount, investment_credit_debt FROM manufacturer WHERE game_id=? AND account_id=?", id, user.getAccount().getId());
        boolean admin = user.getAccount().getRole() == Account.Role.ADMIN;
        if (own.isEmpty() && !admin) throw new org.springframework.security.access.AccessDeniedException("Нет доступа к игре");
        if (own.isEmpty()) {
            own = jdbc.queryForList("SELECT id, balance, investment_credit_amount, investment_credit_debt FROM manufacturer WHERE game_id=? ORDER BY id LIMIT 1", id);
        }
        if (own.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        Long manufacturer = ((Number) own.get(0).get("id")).longValue();
        model.addAttribute("game", games.get(0));
        model.addAttribute("own", own.get(0));
        model.addAttribute("cycles", jdbc.queryForList("""
            SELECT start_date, time_to_market, product_count, price, cost_price, quality_index,
                   assortment, business_credit_amount
            FROM production_parameters WHERE manufacturer_id=? ORDER BY start_date,id
            """, manufacturer));
        model.addAttribute("ads", jdbc.queryForList("SELECT start_date,end_date,intensity_index,cost FROM advertisement WHERE manufacturer_id=? ORDER BY start_date,id", manufacturer));
        model.addAttribute("investmentPayments", jdbc.queryForList("""
            SELECT date, principal_payment, interest_amount FROM investment_credit_payment
            WHERE manufacturer_id=? ORDER BY date,id
            """, manufacturer));
        model.addAttribute("businessPayments", jdbc.queryForList("""
            SELECT b.date, b.amount FROM business_credit_payment b
            JOIN production_parameters p ON p.id=b.production_parameters_id
            WHERE p.manufacturer_id=? ORDER BY b.date,b.id
            """, manufacturer));
        model.addAttribute("taxes", jdbc.queryForList("SELECT date, amount FROM sales_tax_payment WHERE manufacturer_id=? ORDER BY date,id", manufacturer));
        model.addAttribute("days", jdbc.queryForList("""
            SELECT trade_date, products_produced, products_sold, products_in_stock, price, balance,
                   paid_taxes_amount, repaid_investment_credit_amount, repaid_business_credit_amount,
                   current_investment_credit_debt_amount, current_business_credit_debt_amount
            FROM statistics_info WHERE manufacturer_id=? ORDER BY trade_date,id
            """, manufacturer));
        model.addAttribute("result", jdbc.queryForList("SELECT result FROM game_result WHERE manufacturer_id=?", manufacturer));
        model.addAttribute("ranking", jdbc.queryForList("""
            SELECT a.full_name AS name, gr.result FROM game_result gr
            JOIN manufacturer m ON m.id=gr.manufacturer_id JOIN account a ON a.id=m.account_id
            WHERE gr.game_id=? ORDER BY gr.result DESC
            """, id));
        model.addAttribute("advice", jdbc.queryForList("""
            SELECT h.game_day, h.selected_strategy, h.chosen_strategy, h.created_at
            FROM recommendation_history h JOIN manufacturer m ON m.id=h.manufacturer_id
            WHERE m.id=? AND m.save_recommendation_history=true ORDER BY h.game_day,h.id
            """, manufacturer));
        return "game_analysis";
    }
}
