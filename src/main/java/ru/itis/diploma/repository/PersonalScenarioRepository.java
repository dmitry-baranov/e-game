package ru.itis.diploma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.diploma.model.PersonalScenario;
import java.util.List;
import java.util.Optional;

public interface PersonalScenarioRepository extends JpaRepository<PersonalScenario, Long> {
    List<PersonalScenario> findByManufacturerIdOrderByUpdatedAtDesc(Long manufacturerId);
    Optional<PersonalScenario> findByIdAndManufacturerId(Long id, Long manufacturerId);
}
