package ru.itis.diploma.game.model;

import lombok.Data;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Сохранённый ответ рекомендательной системы для одного предприятия. */
@Data
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"manufacturer_id", "request_key"}))
public class RecommendationHistory {
    /** Идентификатор сохранённого запроса. */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Владелец истории рекомендаций. */
    @ManyToOne(optional = false)
    private Manufacturer manufacturer;
    /** Ключ для защиты от повторного сохранения того же запроса. */
    @Column(name = "request_key", nullable = false)
    private String requestKey;
    /** День игры, в который были получены рекомендации. */
    private Integer gameDay;
    /** Курс предприятия на момент выдачи рекомендаций. */
    private String selectedStrategy;
    /** Карточка, которую игрок выбрал из этого ответа; до выбора null. */
    private String chosenStrategy;
    /** Реальное время создания записи. */
    private LocalDateTime createdAt;
    public String getCreatedAtText() { return createdAt == null ? "" : createdAt.toString(); }
    /** JSON ответа с карточками рекомендаций на момент запроса. */
    @Column(columnDefinition = "text")
    private String snapshotJson;
}
