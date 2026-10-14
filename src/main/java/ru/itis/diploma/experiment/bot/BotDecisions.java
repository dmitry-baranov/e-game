package ru.itis.diploma.experiment.bot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.itis.diploma.game.dto.StrategySnapshot;
import ru.itis.diploma.game.model.Manufacturer;
import ru.itis.diploma.game.repository.CycleObservationRepository;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;

/**
 * Граница между движком игры и политикой: бот видит снимок ДО применения решений этого дня,
 * а не скрытые настройки покупателя или планы других ботов. Для policies bots-v6 добавляется
 * история только собственных полностью наблюдавшихся циклов; версии сохранённых серий
 * продолжают использовать прежний порядок входных данных и seed. Запись результатов
 * в серию и журнал находится у вызывающего модуля экспериментов.
 */
@Service
@RequiredArgsConstructor
public class BotDecisions {
    private final BotPolicies policies;
    private final CycleObservationRepository cycleObservations;
    private final ObjectMapper mapper;

    public BotPolicies.Decision decide(long worldSeed, String policyVersion, String modelVersion,
                                       Manufacturer player, StrategySnapshot view, int index) {
        long seed = policySeed(worldSeed, policyVersion, index);
        if (!"bots-v6".equals(policyVersion))
            return policies.decide(view, player.getSelectedStrategy(), seed, index, modelVersion);
        return policies.decide(view, player.getSelectedStrategy(), seed, index, modelVersion,
            observedCycles(player.getId(), view.day()));
    }

    /**
     * После STOCK_SKIP/CREDIT_SKIP не пересчитываем заведомо тот же недопустимый план
     * минимум три дня; позже ждём уменьшения склада или улучшения баланса. В дни
     * без новой попытки отсутствует запись решения — это не потерянная строка журнала.
     */
    public boolean unchangedAfterSkip(String outcome, int previousDay, String previousSnapshot,
                                      StrategySnapshot view) {
        if (!Set.of("STOCK_SKIP", "CREDIT_SKIP").contains(outcome)) return false;
        if (view.day() < previousDay + 3) return true;
        try {
            var before = mapper.readTree(previousSnapshot);
            if ("STOCK_SKIP".equals(outcome))
                return view.stock() >= before.path("stock").asInt();
            return view.balance().compareTo(new BigDecimal(before.path("balance").asText())) <= 0;
        } catch (JsonProcessingException | NumberFormatException e) {
            return false;
        }
    }

    /** Только прошлые полные циклы данного предприятия: дни сделки считаются от старта+1. */
    private List<ObservedCycle> observedCycles(Long manufacturerId, int day) {
        return cycleObservations
            .findByManufacturerIdAndCompleteIsTrueAndEndDateLessThanEqualOrderByStartDateAsc(manufacturerId, day)
            .stream().map(cycle -> {
                var production = cycle.getProductionParameters();
                return new ObservedCycle(cycle.getStartDate(), cycle.getExpectedDays(), production.getProductCount(),
                    production.getPrice(), production.getQualityIndex(), production.getAssortment(),
                    cycle.getAdvertisingIntensity(), cycle.getAdvertisingDays(), cycle.getProductsSold(),
                    cycle.getRevenue());
            }).toList();
    }

    /**
     * Отдельный воспроизводимый seed политики не совпадает с seed генератора мира.
     * Для сохранённых серий старых версий оставлен исторический способ выбора seed.
     */
    private long policySeed(long worldSeed, String policyVersion, int index) {
        if (policyVersion == null ||
            (!policyVersion.equals("bots-v5") && !policyVersion.equals("bots-v6")))
            return worldSeed; // Preserve games started with earlier policy versions.
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update("bot-policy-v1".getBytes(StandardCharsets.UTF_8));
            var bytes = ByteBuffer.allocate(16).putLong(worldSeed).putLong(index).array();
            return ByteBuffer.wrap(digest.digest(bytes)).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }

}
