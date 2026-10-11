package ru.itis.diploma.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import ru.itis.diploma.game.model.CycleObservation;

import java.util.List;

public interface CycleObservationRepository extends JpaRepository<CycleObservation, Long> {
    List<CycleObservation> findByManufacturer_Game_Id(Long gameId);
    @EntityGraph(attributePaths = "productionParameters")
    List<CycleObservation> findByManufacturerIdAndCompleteIsTrueAndEndDateLessThanEqualOrderByStartDateAsc(
        Long manufacturerId, int day);
}
