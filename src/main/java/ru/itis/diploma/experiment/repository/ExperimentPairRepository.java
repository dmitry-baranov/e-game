package ru.itis.diploma.experiment.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import ru.itis.diploma.experiment.model.ExperimentPair;

import java.util.Optional;
import java.util.List;

public interface ExperimentPairRepository extends JpaRepository<ExperimentPair, Long> {
    /**
     * Находит оценочную пару по серии и порядковому номеру.
     *
     * @param seriesId идентификатор серии
     * @param pairNumber номер пары внутри серии
     * @return найденная пара либо пустое значение
     */
    Optional<ExperimentPair> findBySeriesIdAndPairNumber(Long seriesId, int pairNumber);

    /**
     * Загружает пару с пессимистической блокировкой для согласования статусов.
     *
     * @param id идентификатор пары
     * @return заблокированная пара либо пустое значение
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ExperimentPair p where p.id = :id")
    Optional<ExperimentPair> lockById(Long id);

    /**
     * Блокирует пары серии, в которых остались ожидающие запуска попытки.
     *
     * @param seriesId идентификатор серии
     * @return пары с попытками в статусе очереди
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from ExperimentPair p where p.series.id = :seriesId and exists " +
        "(select r.id from ExperimentRun r where r.pair = p and r.status = 'QUEUED')")
    List<ExperimentPair> lockWithQueuedRun(Long seriesId);
}
