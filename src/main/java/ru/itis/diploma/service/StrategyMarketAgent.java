package ru.itis.diploma.service;

import org.springframework.stereotype.Component;
import ru.itis.diploma.dto.StrategySnapshot;
import java.util.Comparator;
import java.util.List;

@Component
public class StrategyMarketAgent {
    public record Evidence(int observations, Integer low, Integer base, Integer high, boolean stockLimited,
                           StrategySnapshot.Competitor cheapestCompetitor) {}

    public Evidence inspect(StrategySnapshot snapshot) {
        List<Integer> sold = snapshot.sales().stream().map(StrategySnapshot.Sale::sold).sorted().toList();
        int n = sold.size();
        StrategySnapshot.Competitor cheapest = snapshot.competitors().stream()
            .min(Comparator.comparing(StrategySnapshot.Competitor::price)).orElse(null);
        if (n < 3) return new Evidence(n, null, null, null,
            snapshot.sales().stream().anyMatch(s -> s.stock() == 0), cheapest);
        return new Evidence(n, sold.get(n >= 7 ? 1 : 0),
            (int) Math.round((sold.get((n - 1) / 2) + sold.get(n / 2)) / 2.0),
            sold.get(n >= 7 ? 5 : n - 1), snapshot.sales().stream().anyMatch(s -> s.stock() == 0), cheapest);
    }
}
