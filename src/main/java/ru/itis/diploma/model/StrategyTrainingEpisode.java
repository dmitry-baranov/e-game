package ru.itis.diploma.model;

import lombok.Getter;
import lombok.Setter;
import javax.persistence.*;

/** Immutable pre-decision view and the action actually taken; labels come from later trade statistics. */
@Entity
@Getter @Setter
public class StrategyTrainingEpisode {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Manufacturer manufacturer;
    private int decisionDay;
    private int cycleDays;
    @Column(columnDefinition = "text", nullable = false)
    private String snapshotJson;
    @Column(columnDefinition = "text", nullable = false)
    private String actionJson;
}
