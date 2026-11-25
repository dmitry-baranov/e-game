package ru.itis.diploma.experiment.repository;

import ru.itis.diploma.experiment.model.BotDecisionLog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BotDecisionLogRepository extends JpaRepository<BotDecisionLog, Long> {
    /**
     * Находит последнее по дню и идентификатору решение бота в заданной попытке.
     *
     * @param runId идентификатор попытки эксперимента
     * @param manufacturerId идентификатор производителя-бота
     * @return последнее решение либо пустое значение, если журнал отсутствует
     */
    Optional<BotDecisionLog> findFirstByRunIdAndManufacturerIdOrderByDecisionDayDescIdDesc(Long runId, Long manufacturerId);
}
