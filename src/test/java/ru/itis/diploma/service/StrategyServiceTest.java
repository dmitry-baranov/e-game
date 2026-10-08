package ru.itis.diploma.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import ru.itis.diploma.model.Manufacturer;
import ru.itis.diploma.repository.ManufacturerRepository;
import ru.itis.diploma.repository.PersonalScenarioRepository;
import ru.itis.diploma.repository.RecommendationHistoryRepository;
import ru.itis.diploma.repository.StrategyTrainingEpisodeRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class StrategyServiceTest {
    @Test
    void cannotChooseAnotherPlayersPersonalScenario() {
        var snapshots = mock(StrategySnapshotService.class);
        var manufacturers = mock(ManufacturerRepository.class);
        var plans = mock(PersonalScenarioRepository.class);
        var history = mock(RecommendationHistoryRepository.class);
        var own = Manufacturer.builder().id(3L).build();
        when(snapshots.owner(1L, 2L)).thenReturn(own);
        when(plans.findByIdAndManufacturerId(7L, 3L)).thenReturn(Optional.empty());
        var service = new StrategyService(snapshots, mock(StrategyCoordinator.class), manufacturers,
            plans, history, mock(StrategyTrainingEpisodeRepository.class),
            mock(StrategyExplanationService.class), new ObjectMapper());
        assertThrows(AccessDeniedException.class, () -> service.select(1L, 2L, "PERSONAL:7", null));
        verifyNoInteractions(manufacturers);
    }

    @Test
    void revokingTrainingConsentDeletesCapturedEpisodesButNotRecommendationHistory() {
        var snapshots = mock(StrategySnapshotService.class);
        var manufacturers = mock(ManufacturerRepository.class);
        var episodes = mock(StrategyTrainingEpisodeRepository.class);
        var history = mock(RecommendationHistoryRepository.class);
        var owner = Manufacturer.builder().id(3L).allowStrategyTraining(true).build();
        when(snapshots.owner(1L, 2L)).thenReturn(owner);
        var service = new StrategyService(snapshots, mock(StrategyCoordinator.class), manufacturers,
            mock(PersonalScenarioRepository.class), history, episodes,
            mock(StrategyExplanationService.class), new ObjectMapper());
        service.trainingPreference(1L, 2L, false);
        assert !Boolean.TRUE.equals(owner.getAllowStrategyTraining());
        verify(episodes).deleteByManufacturerId(3L);
        verifyNoInteractions(history);
    }
}
