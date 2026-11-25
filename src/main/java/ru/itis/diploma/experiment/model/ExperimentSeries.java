package ru.itis.diploma.experiment.model;

import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Сохранённый «план эксперимента»: configJson и версии генератора/политик закрепляются
 * при создании, поэтому пауза и перезапуск приложения не меняют условия следующих игр.
 * nextNumber — следующий номер ПОПЫТКИ, а не число уже завершённых пригодных игр.
 */
@Entity @Getter @Setter
public class ExperimentSeries {
    /** Идентификатор серии в БД. */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Зафиксированные настройки серии в JSON: рынки, политики, лимиты и seed. */
    @Column(columnDefinition = "text", nullable = false)
    private String configJson;
    /** Состояние серии: RUNNING, PAUSED, DONE, EXHAUSTED или STOPPED. */
    private String status = "RUNNING";
    /** Назначение серии: новые игры COLLECT; EVALUATE хранится для старых оценок. */
    private String mode = "COLLECT";
    /** Версия закреплённой модели для исторических оценочных серий; иначе null. */
    private String modelVersion;
    /** Версия генератора рынков, позволяющая воспроизвести следующие попытки. */
    private String generatorVersion;
    /** Версия правил ботов и проверок, сохранённая при создании серии. */
    private String policyVersion;
    /** Сколько завершённых пригодных игр нужно получить. */
    private int target;
    /** Номер следующей попытки, включая ошибки и неполные игры. */
    private int nextNumber = 1;
    /** Дата и время создания серии. */
    private LocalDateTime createdAt = LocalDateTime.now();
}
