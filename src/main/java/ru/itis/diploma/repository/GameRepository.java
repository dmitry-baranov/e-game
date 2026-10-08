package ru.itis.diploma.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import javax.persistence.LockModeType;
import ru.itis.diploma.model.Game;
import ru.itis.diploma.model.enums.GameStatus;

import java.util.List;
import java.util.Optional;

public interface GameRepository extends JpaRepository<Game, Long> {
    List<Game> findByIdIn(List<Long> gameIds);

    Optional<Game> findFirstByStatusNot(GameStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Game g where g.id = :id")
    Optional<Game> lockById(@Param("id") Long id);
}
