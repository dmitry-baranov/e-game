package ru.itis.diploma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.diploma.model.ProductionParameters;

import java.util.List;

public interface ProductionParametersRepository extends JpaRepository<ProductionParameters, Long> {
    List<ProductionParameters> findByManufacturerId(Long manufacturerId);
    long countByManufacturerId(Long manufacturerId);

}
