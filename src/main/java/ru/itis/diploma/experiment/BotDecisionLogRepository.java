package ru.itis.diploma.experiment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BotDecisionLogRepository extends JpaRepository<BotDecisionLog, Long> {
    Optional<BotDecisionLog> findFirstByRunIdAndManufacturerIdOrderByDecisionDayDescIdDesc(Long runId, Long manufacturerId);
}
