package ru.itis.diploma.experiment;

import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;
import java.time.LocalDateTime;

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
    private int nextNumber = 1;
    private LocalDateTime createdAt = LocalDateTime.now();
}
