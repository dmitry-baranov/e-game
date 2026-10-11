package ru.itis.diploma.experiment.repository;

import ru.itis.diploma.experiment.model.BotDecisionLog;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BotDecisionLogRepository extends JpaRepository<BotDecisionLog, Long> {
    Optional<BotDecisionLog> findFirstByRunIdAndManufacturerIdOrderByDecisionDayDescIdDesc(Long runId, Long manufacturerId);
}
