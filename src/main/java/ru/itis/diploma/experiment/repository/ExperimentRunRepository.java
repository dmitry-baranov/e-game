package ru.itis.diploma.experiment.repository;

import ru.itis.diploma.experiment.model.ExperimentRun;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ExperimentRunRepository extends JpaRepository<ExperimentRun, Long> {
    /**
     * Возвращает попытки серии от последней к первой.
     *
     * @param seriesId идентификатор серии
     * @return попытки, отсортированные по убыванию номера
     */
    List<ExperimentRun> findBySeriesIdOrderByNumberDesc(Long seriesId);

    /**
     * Подсчитывает попытки серии с заданным статусом.
     *
     * @param seriesId идентификатор серии
     * @param status статус попытки
     * @return количество соответствующих попыток
     */
    long countBySeriesIdAndStatus(Long seriesId, String status);

    /**
     * Подсчитывает попытки по режиму серии и статусу.
     *
     * @param mode режим серии
     * @param status статус попытки
     * @return количество соответствующих попыток
     */
    long countBySeriesModeAndStatus(String mode, String status);

    /**
     * Находит последнюю попытку серии с указанным статусом.
     *
     * @param seriesId идентификатор серии
     * @param status статус попытки
     * @return последняя попытка либо пустое значение
     */
    Optional<ExperimentRun> findFirstBySeriesIdAndStatusOrderByNumberDesc(Long seriesId, String status);

    /**
     * Возвращает обе попытки, относящиеся к оценочной паре.
     *
     * @param pairId идентификатор пары
     * @return попытки пары
     */
    List<ExperimentRun> findByPairId(Long pairId);

    /**
     * Возвращает незавершённые после перезапуска активные попытки в очередь.
     *
     * @return количество обновлённых попыток
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ExperimentRun r set r.status = 'QUEUED' where r.status = 'RUNNING'")
    int requeueRunning();

    /**
     * Отменяет все ожидающие в очереди попытки указанной серии.
     *
     * @param seriesId идентификатор серии
     * @return количество отменённых попыток
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ExperimentRun r set r.status = 'CANCELLED' " +
        "where r.series.id = :seriesId and r.status = 'QUEUED'")
    int cancelQueuedBySeriesId(Long seriesId);
}
