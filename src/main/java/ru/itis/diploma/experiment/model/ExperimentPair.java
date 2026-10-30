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
    /** Идентификатор пары в БД. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Серия, внутри которой сравнивали правила с моделью. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "series_id", nullable = false)
    private ExperimentSeries series;
    /** Порядковый номер пары в серии. */
    private int pairNumber;
    /** Общий seed обеих сопоставимых игр. */
    private long seed;
    /** Общий рынок обеих игр. */
    private String market;
    /** Состояние пары: PENDING, COMPLETED или INCOMPLETE. */
    private String status = "PENDING";

    /**
     * Завершает одну половину оценочной пары и согласует статусы обеих попыток.
     * Пара становится завершённой только после успешного окончания обоих участников.
     *
     * @param run завершаемая попытка
     * @param members все попытки, входящие в пару
     * @param valid признак пригодности результата завершаемой попытки
     */
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

    /**
     * Помечает пару неполной и аннулирует ожидающие вторую половину попытки.
     *
     * @param members все попытки, входящие в пару
     */
    public void invalidate(List<ExperimentRun> members) {
        status = "INCOMPLETE";
        members.stream().filter(member -> "WAITING_PAIR".equals(member.getStatus()))
            .forEach(member -> member.setStatus("INCOMPLETE"));
    }

    /**
     * Проверяет, исключает ли статус попытки успешное завершение пары.
     *
     * @param runStatus статус попытки
     * @return {@code true} для неполной, ошибочной или отменённой попытки
     */
    private boolean isUnsuccessful(String runStatus) {
        return "INCOMPLETE".equals(runStatus) || "ERROR".equals(runStatus) || "CANCELLED".equals(runStatus);
    }
}
