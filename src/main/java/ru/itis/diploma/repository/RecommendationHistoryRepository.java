package ru.itis.diploma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.diploma.model.RecommendationHistory;
import java.util.List;
import java.util.Optional;

public interface RecommendationHistoryRepository extends JpaRepository<RecommendationHistory, Long> {
    List<RecommendationHistory> findByManufacturerIdOrderByCreatedAtDesc(Long manufacturerId);
    Optional<RecommendationHistory> findByManufacturerIdAndRequestKey(Long manufacturerId, String requestKey);
    Optional<RecommendationHistory> findByIdAndManufacturerId(Long id, Long manufacturerId);
}
