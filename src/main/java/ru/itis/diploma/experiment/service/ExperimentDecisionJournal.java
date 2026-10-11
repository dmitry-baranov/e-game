package ru.itis.diploma.experiment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.itis.diploma.experiment.bot.BotDecisions;
import ru.itis.diploma.experiment.bot.BotPolicies;
import ru.itis.diploma.experiment.model.BotDecisionLog;
import ru.itis.diploma.experiment.model.ExperimentRun;
import ru.itis.diploma.experiment.repository.BotDecisionLogRepository;
import ru.itis.diploma.game.dto.StrategySnapshot;
import ru.itis.diploma.game.model.Manufacturer;

/**
 * Преобразует предложенный ботом план в историю конкретной экспериментальной серии.
 * Бот знает только прошлый снимок и политику, а принадлежность серии, хранение
 * решений и счётчики принятой модели — ответственность оркестратора эксперимента.
 */
@Service
@RequiredArgsConstructor
public class ExperimentDecisionJournal {
    private final BotDecisionLogRepository logs;
    private final BotDecisions botDecisions;
    private final ObjectMapper mapper;

    public boolean unchangedAfterSkip(ExperimentRun run, Manufacturer player, StrategySnapshot view) {
        var previous = logs.findFirstByRunIdAndManufacturerIdOrderByDecisionDayDescIdDesc(
            run.getId(), player.getId()).orElse(null);
        return previous != null && botDecisions.unchangedAfterSkip(previous.getOutcome(),
            previous.getDecisionDay(), previous.getSnapshotJson(), view);
    }

    /** Счётчики применённой модели увеличиваются только после ACCEPTED. */
    public void recordDecision(ExperimentRun run, BotPolicies.Decision decision) {
        if (decision.usedModel()) run.setModelDecisions(run.getModelDecisions() + 1);
        if (decision.fallback()) run.setModelFallbacks(run.getModelFallbacks() + 1);
    }

    /** Снимок, предложение и результат проверки сохраняются в транзакции игрового дня. */
    public BotDecisionLog logDecision(ExperimentRun run, Manufacturer player, StrategySnapshot snapshot,
                                      BotPolicies.Decision decision, String outcome, String modelVersion) {
        try {
            var log = new BotDecisionLog();
            log.setRun(run);
            log.setManufacturer(player);
            log.setDecisionDay(snapshot.day());
            log.setPolicy(player.getSelectedStrategy());
            log.setModelVersion(modelVersion);
            log.setOutcome(outcome);
            log.setUsedModel(decision.usedModel());
            log.setFallback(decision.fallback());
            log.setFallbackReason(decision.fallbackReason());
            log.setRecommendationSource(decision.usedModel() ? "MODEL" :
                decision.fallback() ? "RULES_FALLBACK" : player.getSelectedStrategy());
            log.setSnapshotJson(mapper.writeValueAsString(snapshot));
            log.setProposedActionJson(mapper.writeValueAsString(decision.action()));
            log.setCandidatesJson(mapper.writeValueAsString(decision.candidates()));
            return logs.save(log);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сохранить решение бота", e);
        }
    }

    public void acceptCycle(BotDecisionLog decision, ru.itis.diploma.game.model.ProductionParameters cycle) {
        decision.setAcceptedCycle(cycle);
        cycle.setDecisionLogId(decision.getId());
    }
}
