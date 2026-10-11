package ru.itis.diploma.game.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.itis.diploma.game.model.Account;

import java.util.List;
import java.util.Optional;


public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByEmail(String email);
    List<Account> findByEmailIn(List<String> emails);
    @Query("SELECT a FROM Account a WHERE a.role <> :role " +
        "AND (a.email IS NULL OR a.email NOT LIKE 'bot-%@experiment.invalid') " +
        "ORDER BY a.fullName, a.id")
    List<Account> findGameParticipants(@Param("role") Account.Role excludedRole);
}
