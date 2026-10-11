package ru.itis.diploma.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.itis.diploma.game.model.BusinessCreditPayment;

import java.util.List;

public interface BusinessCreditPaymentRepository extends JpaRepository<BusinessCreditPayment, Long> {
    List<BusinessCreditPayment> findAllByProductionParametersIdInAndNextDate(List<Long> productionParametersIds, Integer nextDate);

    List<BusinessCreditPayment> findAllByProductionParametersIdInAndNextDateAfter(List<Long> productionParametersIds, Integer nextDate);

    List<BusinessCreditPayment> findAllByProductionParametersId(Long productionParametersId);
}
