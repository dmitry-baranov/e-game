package ru.itis.diploma.experiment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.itis.diploma.experiment.bot.BotDecisions;
import ru.itis.diploma.experiment.bot.model.BotDecision;
import ru.itis.diploma.experiment.model.BotDecisionLog;
import ru.itis.diploma.experiment.model.ExperimentRun;
import ru.itis.diploma.experiment.repository.BotDecisionLogRepository;
import ru.itis.diploma.game.dto.StrategySnapshot;
import ru.itis.diploma.game.model.Manufacturer;
import ru.itis.diploma.game.model.ProductionParameters;

import java.util.List;

/**
 * Преобразует предложенный ботом план в историю конкретной экспериментальной серии.
 * Бот знает только прошлый снимок и политику, а принадлежность серии, хранение
 * решений — ответственность оркестратора эксперимента.
 */
@Service
@RequiredArgsConstructor
public class ExperimentDecisionJournal {
    private final BotDecisionLogRepository logs;
    private final BotDecisions botDecisions;
    private final ObjectMapper mapper;

    /**
     * Определяет, следует ли отложить повторное решение после последнего пропуска:
     * в первые три дня повтор блокируется безусловно, затем сравниваются склад и баланс.
     *
     * @param run текущий экспериментальный запуск
     * @param player проверяемый производитель
     * @param view актуальный снимок стратегии
     * @return {@code true}, если повторное предложение пока следует пропустить
     */
    public boolean unchangedAfterSkip(ExperimentRun run, Manufacturer player, StrategySnapshot view) {
        Long runId = run.getId();
        Long manufacturerId = player.getId();
        BotDecisionLog previous = logs.findFirstByRunIdAndManufacturerIdOrderByDecisionDayDescIdDesc(
            runId, manufacturerId).orElse(null);
        if (previous == null) {
            return false;
        }
        return botDecisions.unchangedAfterSkip(previous.getOutcome(),
            previous.getDecisionDay(), previous.getSnapshotJson(), view);
    }

    /**
     * Сохраняет снимок, предложение и результат проверки в транзакции игрового дня.
     *
     * @param run текущий экспериментальный запуск
     * @param player производитель, принявший решение
     * @param snapshot снимок состояния до принятия решения
     * @param decision предложение политики
     * @param outcome результат проверки предложения
     * @param modelVersion версия модели, закреплённая за серией
     * @return сохранённая запись журнала
     * @throws IllegalStateException если решение не удалось сериализовать
     */
    public BotDecisionLog logDecision(ExperimentRun run, Manufacturer player, StrategySnapshot snapshot,
                                       BotDecision decision, String outcome, String modelVersion) {
        try {
            var log = new BotDecisionLog();
            log.setRun(run);
            log.setManufacturer(player);
            log.setDecisionDay(snapshot.day());
            String policy = player.getSelectedStrategy();
            log.setPolicy(policy);
            log.setModelVersion(modelVersion);
            log.setOutcome(outcome);
            log.setUsedModel(false);
            log.setFallback(false);
            log.setRecommendationSource(policy);
            String snapshotJson = mapper.writeValueAsString(snapshot);
            String actionJson = mapper.writeValueAsString(decision.action());
            String candidatesJson = mapper.writeValueAsString(List.of());
            log.setSnapshotJson(snapshotJson);
            log.setProposedActionJson(actionJson);
            log.setCandidatesJson(candidatesJson);
            return logs.save(log);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось сохранить решение бота", e);
        }
    }

    /**
     * Связывает принятую запись решения с созданным производственным циклом в обе стороны.
     * Связь фиксируется в текущей транзакции игрового дня.
     *
     * @param decision сохранённая запись принятого решения
     * @param cycle созданный производственный цикл
     */
    public void acceptCycle(BotDecisionLog decision, ProductionParameters cycle) {
        decision.setAcceptedCycle(cycle);
        cycle.setDecisionLogId(decision.getId());
    }
}
