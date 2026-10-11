package ru.itis.diploma.game.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import ru.itis.diploma.game.dto.CommonProductionParameters;
import ru.itis.diploma.game.dto.StrategySnapshot;
import ru.itis.diploma.game.model.Manufacturer;
import ru.itis.diploma.game.model.StrategyTrainingEpisode;
import ru.itis.diploma.game.repository.StrategyTrainingEpisodeRepository;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StrategyTrainingCapture {
    // Resolve only when production is submitted; avoids a cycle through ManufacturerService.
    private final ObjectProvider<StrategySnapshotService> snapshots;
    private final StrategyTrainingEpisodeRepository episodes;
    private final ObjectMapper mapper;
    private final ThreadLocal<Map<Long, StrategySnapshot>> stagedSnapshots = new ThreadLocal<>();
    private final ThreadLocal<Long> stagedRunId = new ThreadLocal<>();

    /** One atomic experiment turn: preserve each player's view before anyone acts. */
    public void withPreDecisionSnapshots(Map<Long, StrategySnapshot> staged, Runnable actions) {
        withPreDecisionSnapshots(null, staged, actions);
    }

    public void withPreDecisionSnapshots(Long runId, Map<Long, StrategySnapshot> staged, Runnable actions) {
        if (stagedSnapshots.get() != null) throw new IllegalStateException("Повторное открытие хода");
        stagedSnapshots.set(Map.copyOf(staged));
        if (runId != null) stagedRunId.set(runId);
        try { actions.run(); }
        finally {
            stagedSnapshots.remove();
            stagedRunId.remove();
        }
    }

    public StrategyTrainingEpisode capture(Manufacturer owner, CommonProductionParameters action) {
        if (!Boolean.TRUE.equals(owner.getAllowStrategyTraining())) return null;
        var episode = new StrategyTrainingEpisode();
        episode.setManufacturer(owner);
        episode.setRunId(stagedRunId.get());
        episode.setDecisionDay(owner.getGame().getCurrentDay());
        episode.setCycleDays(action.getTimeToMarket());
        try {
            var staged = stagedSnapshots.get();
            StrategySnapshot snapshot = staged == null ? null : staged.get(owner.getAccount().getId());
            episode.setSnapshotJson(mapper.writeValueAsString(snapshot == null ?
                snapshots.getObject().snapshot(owner.getGame().getId(), owner.getAccount().getId()) : snapshot));
            // Only validated, chosen parameters, not user/account objects or simulator internals.
            episode.setActionJson(mapper.writeValueAsString(new Action(action.getProductCount(),
                action.getPrice().doubleValue(), action.getQualityIndex().doubleValue(),
                action.getAssortment(), action.getAdvertisingIntensityIndex(), action.getAdvertisingDays(),
                action.getTimeToMarket())));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Не удалось записать обучающий эпизод", e);
        }
        return episodes.save(episode);
    }

    public void linkCycle(StrategyTrainingEpisode episode, ru.itis.diploma.game.model.ProductionParameters cycle) {
        if (episode == null) return;
        episode.setCycle(cycle);
        episodes.save(episode);
    }

    private record Action(int productCount, double price, double quality, int assortment,
                          int advertisingIntensity, int advertisingDays, int cycleDays) {}
}
