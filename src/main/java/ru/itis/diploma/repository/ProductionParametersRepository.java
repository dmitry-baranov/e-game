package ru.itis.diploma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.diploma.model.ProductionParameters;

import java.util.List;
import java.util.Optional;

public interface ProductionParametersRepository extends JpaRepository<ProductionParameters, Long> {
    List<ProductionParameters> findByManufacturerId(Long manufacturerId);
    Optional<ProductionParameters> findFirstByManufacturerIdOrderByStartDateDescIdDesc(Long manufacturerId);
    long countByManufacturerId(Long manufacturerId);

}
