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
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(columnDefinition = "text", nullable = false)
    private String configJson;
    private String status = "RUNNING";
    private String mode = "COLLECT";
    private String modelVersion;
    private String generatorVersion;
    private String policyVersion;
    private int target;
    private int nextNumber = 1;
    private LocalDateTime createdAt = LocalDateTime.now();
}
