package ru.itis.diploma.experiment.repository;

import ru.itis.diploma.experiment.model.ExperimentRun;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ExperimentRunRepository extends JpaRepository<ExperimentRun, Long> {
    List<ExperimentRun> findBySeriesIdOrderByNumberDesc(Long seriesId);
    long countBySeriesIdAndStatus(Long seriesId, String status);
    long countBySeriesModeAndStatus(String mode, String status);
    Optional<ExperimentRun> findFirstBySeriesIdAndStatusOrderByNumberDesc(Long seriesId, String status);
    List<ExperimentRun> findByPairId(Long pairId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ExperimentRun r set r.status = 'QUEUED' where r.status = 'RUNNING'")
    int requeueRunning();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ExperimentRun r set r.status = 'CANCELLED' " +
        "where r.series.id = :seriesId and r.status = 'QUEUED'")
    int cancelQueuedBySeriesId(Long seriesId);
}
