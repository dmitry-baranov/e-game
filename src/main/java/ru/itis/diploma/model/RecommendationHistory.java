package ru.itis.diploma.model;

import lombok.Data;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Data
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"manufacturer_id", "request_key"}))
public class RecommendationHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false)
    private Manufacturer manufacturer;
    @Column(name = "request_key", nullable = false)
    private String requestKey;
    private Integer gameDay;
    private String selectedStrategy;
    private String chosenStrategy;
    private LocalDateTime createdAt;
    public String getCreatedAtText() { return createdAt == null ? "" : createdAt.toString(); }
    @Column(columnDefinition = "text")
    private String snapshotJson;
}
