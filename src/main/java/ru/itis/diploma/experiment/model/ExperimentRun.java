package ru.itis.diploma.experiment.model;

import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Одна попытка серии и её реальная игра. completedDays позволяет продолжить день
 * после паузы; INCOMPLETE означает непригодную оценочную пару, ERROR — сбой.
 * modelDecisions считает только принятые планы модели, тогда как журнал решений
 * хранит и отклонённые предложения. bestResult — лучший итог ПОСЛЕ долгов.
 */
@Entity @Getter @Setter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"series_id", "number"}),
       indexes = {@Index(columnList = "game_id"), @Index(columnList = "pair_id"),
           @Index(columnList = "series_id,status,number")})
public class ExperimentRun {
    /** Идентификатор попытки в БД. */
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    /** Серия, породившая эту попытку. */
    @ManyToOne(optional = false)
    private ExperimentSeries series;
    /** Пара старой оценки EVALUATE; у обычной COLLECT-игры отсутствует. */
    @ManyToOne
    @JoinColumn(name = "pair_id")
    private ExperimentPair pair;
    /** Порядковый номер попытки внутри серии, начиная с единицы. */
    private int number;
    /** Seed конкретного сценария; позволяет повторить условия мира. */
    private long seed;
    /** Состояние попытки: например RUNNING, QUEUED, COMPLETED, ERROR или INCOMPLETE. */
    private String status = "RUNNING";
    /** Созданная для попытки игровая партия. */
    @OneToOne
    private ru.itis.diploma.game.model.Game game;
    /** Человекочитаемое название выбранного рынка. */
    private String market;
    /** Число ботов в игре этой попытки. */
    private int bots;
    /** День, по достижении которого игру завершают. */
    private int horizon;
    /** Число уже проведённых торговых дней; используется при возобновлении. */
    private int completedDays;
    /** Суммарное число производственных циклов участников. */
    private int cycles;
    /** Исторический счётчик принятых модельных решений; без модели не увеличивается. */
    private Integer modelDecisions = 0;
    /** Исторический счётчик переходов от модели к правилам. */
    private Integer modelFallbacks = 0;
    /** Число технических ошибок запуска, по которым решают о повторе или ERROR. */
    private Integer attempts = 0;
    /** Число предложений, отклонённых из-за остатков на складе. */
    private Integer stockSkips = 0;
    /** Число предложений, отклонённых из-за кредитного лимита. */
    private Integer creditSkips = 0;
    /** Лучший итоговый результат участника после долгов. */
    private BigDecimal bestResult;
    /** Текст последней технической ошибки попытки, если она произошла. */
    @Column(columnDefinition = "text")
    private String error;

    /**
     * Возвращает число принятых решений модели, трактуя старое значение {@code null} как ноль.
     *
     * @return число решений модели
     */
    public int getModelDecisions() { return modelDecisions == null ? 0 : modelDecisions; }

    /**
     * Возвращает число откатов от модели к резервной политике, трактуя {@code null} как ноль.
     *
     * @return число резервных решений
     */
    public int getModelFallbacks() { return modelFallbacks == null ? 0 : modelFallbacks; }

    /**
     * Возвращает число технических сбоев запуска, трактуя {@code null} как ноль.
     *
     * @return число технических попыток запуска
     */
    public int getAttempts() { return attempts == null ? 0 : attempts; }

    /**
     * Возвращает число пропусков из-за товарного запаса, трактуя {@code null} как ноль.
     *
     * @return число пропусков из-за запаса
     */
    public int getStockSkips() { return stockSkips == null ? 0 : stockSkips; }

    /**
     * Возвращает число пропусков из-за кредитных ограничений, трактуя {@code null} как ноль.
     *
     * @return число пропусков из-за кредита
     */
    public int getCreditSkips() { return creditSkips == null ? 0 : creditSkips; }
}
