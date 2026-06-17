package com.sandship.stability.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ship_comparisons", indexes = {
    @Index(name = "idx_ship_comparison_time", columnList = "created_at DESC")
})
public class ShipComparison {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "comparison_name", length = 200)
    private String comparisonName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ship_ids", columnDefinition = "jsonb", nullable = false)
    private List<UUID> shipIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "comparison_criteria", columnDefinition = "jsonb", nullable = false)
    private List<String> comparisonCriteria;

    @Column(name = "loading_condition", nullable = false, length = 50)
    private String loadingCondition = "FULL_LOAD";

    @Column(name = "reference_wave_height", precision = 8, scale = 2)
    private BigDecimal referenceWaveHeight = new BigDecimal("3.0");

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "results", columnDefinition = "jsonb")
    private Map<String, Object> results;

    @Column(name = "ranking_summary", columnDefinition = "TEXT")
    private String rankingSummary;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
