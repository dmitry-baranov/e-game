package ru.itis.diploma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.diploma.model.StrategyTrainingEpisode;

public interface StrategyTrainingEpisodeRepository extends JpaRepository<StrategyTrainingEpisode, Long> {
    void deleteByManufacturerId(Long manufacturerId);
    long countByManufacturerId(Long manufacturerId);
}
