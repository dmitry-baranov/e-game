package ru.itis.diploma.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import ru.itis.diploma.dto.CommonProductionParameters;
import ru.itis.diploma.dto.StrategySnapshot;
import ru.itis.diploma.model.Manufacturer;
import ru.itis.diploma.model.StrategyTrainingEpisode;
import ru.itis.diploma.repository.StrategyTrainingEpisodeRepository;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StrategyTrainingCapture {
    // Resolve only when production is submitted; avoids a cycle through ManufacturerService.
    private final ObjectProvider<StrategySnapshotService> snapshots;
    private final StrategyTrainingEpisodeRepository episodes;
    private final ObjectMapper mapper;
    private final ThreadLocal<Map<Long, StrategySnapshot>> stagedSnapshots = new ThreadLocal<>();

    /** One atomic experiment turn: preserve each player's view before anyone acts. */
    public void withPreDecisionSnapshots(Map<Long, StrategySnapshot> staged, Runnable actions) {
        if (stagedSnapshots.get() != null) throw new IllegalStateException("Повторное открытие хода");
        stagedSnapshots.set(Map.copyOf(staged));
        try { actions.run(); }
        finally { stagedSnapshots.remove(); }
    }

    public void capture(Manufacturer owner, CommonProductionParameters action) {
        if (!Boolean.TRUE.equals(owner.getAllowStrategyTraining())) return;
        var episode = new StrategyTrainingEpisode();
        episode.setManufacturer(owner);
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
        episodes.save(episode);
    }

    private record Action(int productCount, double price, double quality, int assortment,
                          int advertisingIntensity, int advertisingDays, int cycleDays) {}
}
