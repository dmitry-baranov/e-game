package ru.itis.diploma.experiment;

import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;

/** One immutable decision opportunity, including actions rejected before production. */
@Entity @Getter @Setter
@Table(indexes = @Index(columnList = "run_id,manufacturer_id,decisionDay"))
public class BotDecisionLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) private ExperimentRun run;
    @ManyToOne(optional = false) private ru.itis.diploma.model.Manufacturer manufacturer;
    private int decisionDay;
    private String policy;
    private String modelVersion;
    private String outcome;
    private boolean usedModel;
    private boolean fallback;
    private String fallbackReason;
    private String recommendationSource;
    @Column(columnDefinition = "text", nullable = false) private String snapshotJson;
    @Column(columnDefinition = "text", nullable = false) private String proposedActionJson;
    @Column(columnDefinition = "text") private String candidatesJson;
}
