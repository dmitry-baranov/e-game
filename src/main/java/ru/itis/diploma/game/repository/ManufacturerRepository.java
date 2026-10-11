package ru.itis.diploma.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.diploma.game.model.Manufacturer;

import java.util.List;

public interface ManufacturerRepository extends JpaRepository<Manufacturer, Long> {
    List<Manufacturer> findByAccount_Id(Long accountId);
    Manufacturer findByAccount_IdAndGame_Id(Long accountId, Long gameId);

    List<Manufacturer> findByGame_IdAndEnteredInitialProductionParametersIsTrue(Long gameId);

    List<Manufacturer> findByGame_Id(Long gameId);
}
