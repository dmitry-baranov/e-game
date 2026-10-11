package ru.itis.diploma.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ru.itis.diploma.game.model.SalesTaxPayment;

import java.util.Optional;

public interface SalesTaxPaymentRepository extends JpaRepository<SalesTaxPayment, Long> {

    @Query(nativeQuery = true, value = "select * from sales_tax_payment where manufacturer_id = :manufacturerId order by date desc limit 1")
    Optional<SalesTaxPayment> findLastByManufacturerId(Long manufacturerId);

}
