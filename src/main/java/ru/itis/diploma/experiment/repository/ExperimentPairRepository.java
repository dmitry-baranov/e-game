package ru.itis.diploma.experiment.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import ru.itis.diploma.experiment.model.ExperimentPair;

import java.util.Optional;
import java.util.List;

public interface ExperimentPairRepository extends JpaRepository<ExperimentPair, Long> {
    Optional<ExperimentPair> findBySeriesIdAndPairNumber(Long seriesId, int pairNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ExperimentPair p where p.id = :id")
    Optional<ExperimentPair> lockById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ExperimentPair p where p.series.id = :seriesId and exists " +
        "(select r.id from ExperimentRun r where r.pair = p and r.status = 'QUEUED')")
    List<ExperimentPair> lockWithQueuedRun(Long seriesId);
}
