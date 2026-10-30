package ru.itis.diploma.game.model;

import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;

/** Снимок до решения и фактически принятое действие; продажи для обучения поступят позже. */
@Entity
@Table(indexes = {@Index(columnList = "manufacturer_id,decisionDay"), @Index(columnList = "run_id")},
    uniqueConstraints = @UniqueConstraint(columnNames = {"run_id", "cycle_id"}))
@Getter @Setter
public class StrategyTrainingEpisode {
    /** Идентификатор обучающего эпизода. */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Предприятие, принявшее решение. */
    @ManyToOne(optional = false)
    private Manufacturer manufacturer;
    /** ID экспериментальной попытки; скаляр сохраняет независимость game от experiment. */
    @Column(name = "run_id")
    private Long runId;
    /** Производственный цикл, созданный принятым решением. */
    @OneToOne
    private ProductionParameters cycle;
    /** День, снимок которого использован для решения. */
    private int decisionDay;
    /** Рассчитанная продолжительность создаваемого цикла в днях. */
    private int cycleDays;
    /** JSON доступных участнику данных до применения решения. */
    @Column(columnDefinition = "text", nullable = false)
    private String snapshotJson;
    /** JSON фактически принятого плана производства. */
    @Column(columnDefinition = "text", nullable = false)
    private String actionJson;
}
