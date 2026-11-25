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

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
/** Сообщение участнику об игровом событии в конкретный день. */
public class GameNotifications {
    /** Идентификатор уведомления. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Предприятие-получатель уведомления. */
    @ManyToOne
    private Manufacturer manufacturer;

    /** Номер игрового дня события. */
    private Integer date;

    /** Текст уведомления игроку. */
    private String message;

}
