package ru.itis.diploma.experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import ru.itis.diploma.experiment.model.ExperimentSeries;
import ru.itis.diploma.experiment.repository.ExperimentPairRepository;
import ru.itis.diploma.experiment.repository.ExperimentRunRepository;
import ru.itis.diploma.experiment.repository.ExperimentSeriesRepository;
import ru.itis.diploma.experiment.service.ExperimentEngine;
import ru.itis.diploma.experiment.service.ExperimentManager;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExperimentRecoveryTest {
    @Test
    void recoveryUsesSingleBulkUpdate() {
        var series = mock(ExperimentSeriesRepository.class);
        var runs = mock(ExperimentRunRepository.class);
        var transactions = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            invocation.<java.util.function.Consumer<TransactionStatus>>getArgument(0)
                .accept(mock(TransactionStatus.class));
            return null;
        }).when(transactions).executeWithoutResult(any());
        var manager = new ExperimentManager(series, runs, mock(ExperimentPairRepository.class),
            mock(ExperimentEngine.class), transactions, new ObjectMapper());

        manager.recover();

        verify(runs).requeueRunning();
    }

    @Test
    void stopCancelsQueuedRunsWithBulkUpdate() {
        var series = mock(ExperimentSeriesRepository.class);
        var runs = mock(ExperimentRunRepository.class);
        var pairs = mock(ExperimentPairRepository.class);
        var group = new ExperimentSeries();
        group.setStatus("RUNNING");
        when(series.lockById(7L)).thenReturn(java.util.Optional.of(group));
        when(pairs.lockWithQueuedRun(7L)).thenReturn(java.util.List.of());
        var manager = new ExperimentManager(series, runs, pairs, mock(ExperimentEngine.class),
            mock(TransactionTemplate.class), new ObjectMapper());
        org.springframework.test.util.ReflectionTestUtils.setField(manager, "enabled", true);

        manager.change(7L, "stop");

        verify(runs).cancelQueuedBySeriesId(7L);
    }
}
