package ru.itis.diploma.experiment.repository;

import ru.itis.diploma.experiment.model.ExperimentSeries;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ExperimentSeriesRepository extends JpaRepository<ExperimentSeries, Long> {
    List<ExperimentSeries> findAllByOrderByIdDesc();
    List<ExperimentSeries> findByStatusOrderByIdDesc(String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ExperimentSeries s where s.id = :id")
    Optional<ExperimentSeries> lockById(Long id);
}
