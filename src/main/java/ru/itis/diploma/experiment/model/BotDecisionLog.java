package ru.itis.diploma.experiment.model;

import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;

/** Запись одного предложенного решения бота, в том числе отклонённого до производства. */
@Entity @Getter @Setter
@Table(indexes = @Index(columnList = "run_id,manufacturer_id,decisionDay"))
public class BotDecisionLog {
    /** Идентификатор записи журнала. */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Попытка эксперимента, в которой сделано предложение. */
    @ManyToOne(optional = false) private ExperimentRun run;
    /** Предприятие, предложившее план. */
    @ManyToOne(optional = false) private ru.itis.diploma.game.model.Manufacturer manufacturer;
    /** Созданный по принятому плану цикл; для отклонённого плана null. */
    @OneToOne private ru.itis.diploma.game.model.ProductionParameters acceptedCycle;
    /** Игровой день, на снимке которого рассчитан план. */
    private int decisionDay;
    /** Имя политики бота, например CAUTIOUS, PRICE или STOCK. */
    private String policy;
    /** Версия модели в серии старой оценки; у COLLECT может отсутствовать. */
    private String modelVersion;
    /** Результат проверки: ACCEPTED, STOCK_SKIP или CREDIT_SKIP. */
    private String outcome;
    /** Исторический признак применения ML-модели; у текущих ботов false. */
    private boolean usedModel;
    /** Исторический признак замены модели правилами; у текущих ботов false. */
    private boolean fallback;
    /** Причина исторического возврата к правилам; у текущих ботов null. */
    private String fallbackReason;
    /** Источник плана: у текущих ботов совпадает с именем политики. */
    private String recommendationSource;
    /** JSON доступного боту снимка до принятия решения. */
    @Column(columnDefinition = "text", nullable = false) private String snapshotJson;
    /** JSON предложенных параметров производства, даже если план отклонён. */
    @Column(columnDefinition = "text", nullable = false) private String proposedActionJson;
    /** JSON карточек-кандидатов старой рекомендательной системы; сейчас пустой список. */
    @Column(columnDefinition = "text") private String candidatesJson;
}
