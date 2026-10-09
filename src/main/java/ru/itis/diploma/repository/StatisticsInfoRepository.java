package ru.itis.diploma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.diploma.model.StatisticsInfo;
import java.util.List;
import org.springframework.data.domain.Pageable;

public interface StatisticsInfoRepository extends JpaRepository<StatisticsInfo, Long> {
    long countByManufacturer_Game_Id(Long gameId);
    List<StatisticsInfo> findByManufacturerIdOrderByTradeDateDesc(Long manufacturerId);
    List<StatisticsInfo> findByManufacturerIdOrderByTradeDateDesc(Long manufacturerId, Pageable page);
}
