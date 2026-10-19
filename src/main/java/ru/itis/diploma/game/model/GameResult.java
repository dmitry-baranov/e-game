package ru.itis.diploma.game.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
/** Итог одного производителя после завершения игры, с учётом оставшихся долгов. */
public class GameResult {

    /** Идентификатор итоговой записи. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Завершённая игра. */
    @ManyToOne
    private Game game;

    /** Производитель, для которого посчитан результат. */
    @ManyToOne
    private Manufacturer manufacturer;

    /** Финансовый результат предприятия после учёта долгов. */
    private BigDecimal result;

}
