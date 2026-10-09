package ru.itis.diploma.experiment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ExperimentRunRepository extends JpaRepository<ExperimentRun, Long> {
    List<ExperimentRun> findBySeriesIdOrderByNumberDesc(Long seriesId);
    long countBySeriesIdAndStatus(Long seriesId, String status);
    long countBySeriesModeAndStatus(String mode, String status);
    Optional<ExperimentRun> findBySeriesIdAndNumber(Long seriesId, int number);
    Optional<ExperimentRun> findFirstBySeriesIdAndStatusOrderByNumberDesc(Long seriesId, String status);
}
