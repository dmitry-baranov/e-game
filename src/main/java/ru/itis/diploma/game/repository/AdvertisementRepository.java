package ru.itis.diploma.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.diploma.game.model.Advertisement;

import java.util.List;
import java.util.Optional;

public interface AdvertisementRepository extends JpaRepository<Advertisement, Long> {

    List<Advertisement> findByManufacturerId(Long manufacturerId);

    Optional<Advertisement> findFirstByManufacturerIdOrderByStartDateDescIdDesc(Long manufacturerId);

    Optional<Advertisement> findByManufacturerIdAndStartDate(Long manufacturerId, Integer startDate);

    List<Advertisement> findByManufacturer_Game_IdOrderByStartDateAscIdAsc(Long gameId);

}
