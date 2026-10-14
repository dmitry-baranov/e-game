package ru.itis.diploma.game.model;

import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;

/** Immutable pre-decision view and the action actually taken; labels come from later trade statistics. */
@Entity
@Table(indexes = {@Index(columnList = "manufacturer_id,decisionDay"), @Index(columnList = "run_id")},
    uniqueConstraints = @UniqueConstraint(columnNames = {"run_id", "cycle_id"}))
@Getter @Setter
public class StrategyTrainingEpisode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Manufacturer manufacturer;
    /** Scalar experiment id avoids an upstream dependency from game to experiment. */
    @Column(name = "run_id")
    private Long runId;
    @OneToOne
    private ProductionParameters cycle;
    private int decisionDay;
    private int cycleDays;
    @Column(columnDefinition = "text", nullable = false)
    private String snapshotJson;
    @Column(columnDefinition = "text", nullable = false)
    private String actionJson;
}
