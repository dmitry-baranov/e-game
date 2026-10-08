package ru.itis.diploma.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.itis.diploma.dto.StrategyAdvice;
import ru.itis.diploma.dto.StrategyPlan;
import ru.itis.diploma.dto.StrategySnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class StrategyCoordinator {
    private final StrategyMarketAgent marketAgent;
    private final StrategyProductionAgent productionAgent;
    private final StrategyFinancialAgent financialAgent;
    private final StrategyPredictor predictor;

    public StrategyAdvice recommend(StrategySnapshot snapshot, StrategyPlan personal) {
        var evidence = marketAgent.inspect(snapshot);
        var result = new StrategyAdvice();
        result.setGameDay(snapshot.day());
        result.setGeneratedAt(LocalDateTime.now());
        result.setSelectedStrategy(snapshot.selectedStrategy());
        List<StrategyAdvice.Card> cards = new ArrayList<>();
        if (personal != null) {
            cards.add(card(snapshot, personal, "PERSONAL:" + personal.getId(), personal.getName(),
                "Ваш план; оценка при текущих видимых ценах.", "Изменение условий рынка не включено в расчёт.", evidence));
        }
        if (!snapshot.opened()) {
            cards.add(start(snapshot, "CAUTIOUS", "Осторожный старт", 10, 10, "0.4", 1, 0, "1.8",
                "Небольшая партия и минимум расходов.", "Можно упустить спрос.", evidence));
            cards.add(start(snapshot, "AFFORDABLE", "Доступная цена", 20, 10, "0.5", 2, 0, "1.3",
                "Цена выше себестоимости, но маржа меньше.", "Продажи при снижении цены не гарантированы.", evidence));
            cards.add(start(snapshot, "QUALITY", "Качество и продвижение", 30, 10, "0.8", 3, 1, "2",
                "Выше качество и ограниченная реклама.", "Больше затрат и кредита без гарантии продаж.", evidence));
        } else {
            int count = snapshot.productCount();
            int capacity = snapshot.capacity();
            cards.add(card(snapshot, plan(count, capacity, snapshot.price(), snapshot.quality(),
                    snapshot.assortment(), snapshot.advertisingIntensity(), 0), "HOLD", "Сохранить курс",
                "Без смены цены и партии; дождитесь результатов следующего цикла.",
                "Остаток товара может накапливаться.", evidence));
            boolean expand = evidence.high() != null && snapshot.stock() <= count / 5 &&
                evidence.high() * Math.max(1, snapshot.cycleDays()) >= count * 0.8;
            int changed = expand ? count + Math.max(1, count / 5) : Math.max(1, count - Math.max(1, count / 5));
            if (changed != count) cards.add(card(snapshot, plan(changed, capacity, snapshot.price(), snapshot.quality(),
                    Math.min(changed, snapshot.assortment()), snapshot.advertisingIntensity(), 0),
                expand ? "EXPAND" : "PRESERVE", expand ? "Увеличить выпуск" : "Сохранить ликвидность",
                expand ? "Недавние продажи и небольшой остаток позволяют сравнить партию на 20% больше." :
                    "Партия на 20% меньше: ниже известные расходы.",
                expand ? "Расходы и риск непроданного товара растут." :
                    "При высоких продажах товара может не хватить.", evidence));
            if (evidence.cheapestCompetitor() != null && snapshot.price().compareTo(
                evidence.cheapestCompetitor().price()) > 0) {
                cards.add(card(snapshot, plan(count, capacity, snapshot.price().multiply(new BigDecimal("0.90"))
                        .setScale(2, RoundingMode.HALF_UP), snapshot.quality(), snapshot.assortment(),
                        snapshot.advertisingIntensity(), 0), "PRICE", "Проверить цену",
                    "Снижение цены на 10% для сравнения с рынком.", "Маржа уменьшается; спрос может не вырасти.", evidence));
            } else if (snapshot.advertisingIntensity() < 7) {
                cards.add(card(snapshot, plan(count, capacity, snapshot.price(), snapshot.quality(),
                    snapshot.assortment(), snapshot.advertisingIntensity() + 1, 1),
                    "PROMOTE", "Осторожное продвижение", "Реклама на один уровень выше.",
                    "Дополнительные затраты без гарантии спроса.", evidence));
            }
        }
        List<StrategyAdvice.Card> available = cards.stream().filter(c -> c != null).limit(3).toList();
        List<StrategyAdvice.Card> candidates = available;
        predictor.predict(snapshot, candidates).ifPresent(predictions -> {
            for (int i = 0; i < candidates.size(); i++) {
                var estimate = predictions.estimates().get(i);
                if (estimate == null) continue;
                var card = candidates.get(i);
                int supply = snapshot.stock() + card.getProductCount();
                if (estimate.low() < 0 || estimate.low() > estimate.typical() ||
                    estimate.typical() > estimate.high() || estimate.high() > supply) continue;
                card.setSource("MODEL");
                card.setModelName(predictions.name());
                card.setModelVersion(predictions.version());
                card.setModelLowSales(estimate.low());
                card.setModelTypicalSales(estimate.typical());
                card.setModelHighSales(estimate.high());
                card.setAssumption(card.getAssumption() + " Прогноз модели относится к завершению следующего цикла; " +
                    "оценки для ещё не выбранного плана не подтверждены фактическими продажами.");
                financialAgent.modelCash(snapshot, card);
            }
            if (candidates.stream().allMatch(c -> "MODEL".equals(c.getSource()) && c.getLowCash() != null)) {
                List<StrategyAdvice.ModelOpinion> opinions = new ArrayList<>();
                var champion = candidates.stream().max(Comparator.comparing(StrategyAdvice.Card::getLowCash)).orElseThrow();
                opinions.add(opinion(predictions.name(), predictions.version(), champion.getName(),
                    "Выбран по осторожной оценке денег после расходов и известных платежей."));
                if (predictions.comparisons() != null) for (var comparison : predictions.comparisons()) {
                    if (comparison.estimates() == null || comparison.estimates().size() != candidates.size()) continue;
                    int bestIndex = -1;
                    BigDecimal bestCash = null;
                    for (int i = 0; i < candidates.size(); i++) {
                        var estimate = comparison.estimates().get(i);
                        var card = candidates.get(i);
                        if (estimate == null || estimate.low() < 0 || estimate.high() > snapshot.stock() + card.getProductCount() ||
                            estimate.low() > estimate.typical() || estimate.typical() > estimate.high() ||
                            card.getCredit().signum() > 0) { bestIndex = -1; break; }
                        BigDecimal cash = financialAgent.cashForModelUnits(snapshot, card, estimate.low());
                        if (bestCash == null || cash.compareTo(bestCash) > 0) {
                            bestCash = cash;
                            bestIndex = i;
                        }
                    }
                    if (bestIndex >= 0) opinions.add(opinion(comparison.name(), comparison.version(),
                        candidates.get(bestIndex).getName(),
                        bestIndex == candidates.indexOf(champion) ? "Совпадает с действующей моделью." :
                            "Другой прогноз продаж приводит к иному осторожному выбору."));
                }
                result.setModelOpinions(opinions);
            }
        });
        // Rank only comparable, validated estimates. If unavailable, retain the existing rule order.
        if (available.size() > 1 && available.stream().allMatch(c -> "MODEL".equals(c.getSource()) && c.getLowCash() != null) &&
            personal == null) {
            available = available.stream().sorted((a, b) -> b.getLowCash().compareTo(a.getLowCash())).toList();
        }
        result.setRecommendations(available);
        return result;
    }

    private StrategyAdvice.ModelOpinion opinion(String model, String version, String action, String rationale) {
        var opinion = new StrategyAdvice.ModelOpinion();
        opinion.setModel(model); opinion.setVersion(version);
        opinion.setAction(action); opinion.setRationale(rationale);
        return opinion;
    }

    private StrategyAdvice.Card start(StrategySnapshot s, String key, String name, int count, int capacity,
                                      String quality, int assortment, int ads, String margin,
                                      String reason, String risk, StrategyMarketAgent.Evidence evidence) {
        BigDecimal cost = s.baseCost().multiply(new BigDecimal(quality)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal price = cost.multiply(new BigDecimal(margin)).max(new BigDecimal("0.01"))
            .setScale(2, RoundingMode.HALF_UP);
        return card(s, plan(count, capacity, price, new BigDecimal(quality), assortment, ads, ads),
            key, name, reason, risk, evidence);
    }

    private StrategyPlan plan(int count, int capacity, BigDecimal price, BigDecimal quality, int assortment,
                              int ads, int days) {
        var p = new StrategyPlan();
        p.setProductCount(count); p.setCapacity(capacity); p.setPrice(price); p.setQuality(quality);
        p.setAssortment(assortment); p.setAdvertisingIntensity(ads);
        p.setAdvertisingDays(ads == 0 ? 0 : Math.max(1, days));
        return p;
    }

    private StrategyAdvice.Card card(StrategySnapshot s, StrategyPlan p, String key, String name,
                                     String reason, String risk, StrategyMarketAgent.Evidence evidence) {
        var c = new StrategyAdvice.Card();
        c.setKey(key); c.setName(name); c.setReason(reason); c.setRisk(risk);
        c.setProductCount(p.getProductCount()); c.setCapacity(p.getCapacity()); c.setPrice(p.getPrice());
        c.setQuality(p.getQuality()); c.setAssortment(p.getAssortment());
        c.setAdvertisingIntensity(p.getAdvertisingIntensity()); c.setAdvertisingDays(p.getAdvertisingDays());
        c.setCredit(p.getBusinessCreditAmount() == null ? BigDecimal.ZERO : p.getBusinessCreditAmount());
        productionAgent.evaluate(s, c);
        if (s.opened() && !key.startsWith("PERSONAL:") && c.getPrice().compareTo(
            productionAgent.unitCost(s.baseCost(), c)) <= 0) return null;
        if (key.startsWith("PERSONAL:") && c.getPrice().compareTo(
            productionAgent.unitCost(s.baseCost(), c)) <= 0)
            c.setRisk(c.getRisk() + " Цена не покрывает расчётную себестоимость единицы.");
        c.setObservations(evidence.observations());
        c.setLowSales(evidence.low()); c.setNormalSales(evidence.base()); c.setHighSales(evidence.high());
        c.setAssumption("Наблюдаемые продажи за последние " + evidence.observations() +
            " суток; изменения спроса от новой цены/рекламы неизвестны." +
            (evidence.observations() >= 3 && evidence.observations() < 7 ?
                " Короткая история: оценка имеет низкую надёжность." : "") +
            (evidence.low() != null && evidence.low().equals(evidence.high()) ?
                " Одинаковые продажи не означают отсутствия неопределённости." : "") +
            (evidence.stockLimited() ? " Часть дней товар заканчивался — спрос мог быть выше." : "") +
            (evidence.cheapestCompetitor() == null ? " Цены конкурентов пока неизвестны." :
                " Минимальная видимая цена конкурента: " + evidence.cheapestCompetitor().price() +
                    ", качество: " + evidence.cheapestCompetitor().quality() +
                    ", ассортимент: " + evidence.cheapestCompetitor().assortment() +
                    ", реклама: " + evidence.cheapestCompetitor().advertisingIntensity() + "."));
        if (!financialAgent.evaluate(s, c, evidence)) return null;
        return c;
    }
}
