package ru.itis.diploma.experiment;

import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Getter @Setter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"series_id", "number"}),
       indexes = @Index(columnList = "game_id"))
public class ExperimentRun {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private ExperimentSeries series;
    private int number;
    private long seed;
    private String status = "RUNNING";
    @OneToOne
    private ru.itis.diploma.model.Game game;
    private String market;
    private int bots;
    private int horizon;
    private int completedDays;
    private int cycles;
    private Integer modelDecisions = 0;
    private Integer modelFallbacks = 0;
    private Integer attempts = 0;
    private Integer stockSkips = 0;
    private Integer creditSkips = 0;
    private BigDecimal bestResult;
    @Column(columnDefinition = "text")
    private String error;

    public int getModelDecisions() { return modelDecisions == null ? 0 : modelDecisions; }
    public int getModelFallbacks() { return modelFallbacks == null ? 0 : modelFallbacks; }
    public int getAttempts() { return attempts == null ? 0 : attempts; }
    public int getStockSkips() { return stockSkips == null ? 0 : stockSkips; }
    public int getCreditSkips() { return creditSkips == null ? 0 : creditSkips; }
}
