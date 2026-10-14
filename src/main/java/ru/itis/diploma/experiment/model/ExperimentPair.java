package ru.itis.diploma.experiment.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Две сопоставимые попытки EVALUATE с общими условиями сценария. */
@Entity
@Getter
@Setter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"series_id", "pair_number"}))
public class ExperimentPair {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    @JoinColumn(name = "series_id", nullable = false)
    private ExperimentSeries series;
    private int pairNumber;
    private long seed;
    private String market;
    private String status = "PENDING";

    public void finish(ExperimentRun run, List<ExperimentRun> members, boolean valid) {
        var partner = members.stream().filter(member -> member != run).findFirst().orElse(null);
        if (!valid || "INCOMPLETE".equals(status) || partner != null && isUnsuccessful(partner.getStatus())) {
            run.setStatus("INCOMPLETE");
            invalidate(members);
        } else if (partner != null && "WAITING_PAIR".equals(partner.getStatus())) {
            partner.setStatus("COMPLETED");
            run.setStatus("COMPLETED");
            status = "COMPLETED";
        } else {
            run.setStatus("WAITING_PAIR");
        }
    }

    public void invalidate(List<ExperimentRun> members) {
        status = "INCOMPLETE";
        members.stream().filter(member -> "WAITING_PAIR".equals(member.getStatus()))
            .forEach(member -> member.setStatus("INCOMPLETE"));
    }

    private boolean isUnsuccessful(String runStatus) {
        return "INCOMPLETE".equals(runStatus) || "ERROR".equals(runStatus) || "CANCELLED".equals(runStatus);
    }
}
