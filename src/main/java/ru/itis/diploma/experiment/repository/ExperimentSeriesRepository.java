package ru.itis.diploma.experiment.repository;

import ru.itis.diploma.experiment.model.ExperimentSeries;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ExperimentSeriesRepository extends JpaRepository<ExperimentSeries, Long> {
    /**
     * Возвращает все серии от последней к первой.
     *
     * @return серии, отсортированные по убыванию идентификатора
     */
    List<ExperimentSeries> findAllByOrderByIdDesc();

    /**
     * Возвращает серии с заданным статусом от последней к первой.
     *
     * @param status статус серии
     * @return соответствующие серии в обратном порядке создания
     */
    List<ExperimentSeries> findByStatusOrderByIdDesc(String status);

    /**
     * Загружает серию с пессимистической блокировкой для изменения.
     *
     * @param id идентификатор серии
     * @return заблокированная серия либо пустое значение
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ExperimentSeries s where s.id = :id")
    Optional<ExperimentSeries> lockById(Long id);
}
