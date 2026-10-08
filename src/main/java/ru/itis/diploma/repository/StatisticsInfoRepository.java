package ru.itis.diploma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.diploma.model.StatisticsInfo;
import java.util.List;

public interface StatisticsInfoRepository extends JpaRepository<StatisticsInfo, Long> {
    List<StatisticsInfo> findByManufacturerIdOrderByTradeDateDesc(Long manufacturerId);
}
