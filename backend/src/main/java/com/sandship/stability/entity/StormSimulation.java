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
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "storm_simulations", indexes = {
    @Index(name = "idx_storm_sim_ship_time", columnList = "ship_id, simulation_time DESC"),
    @Index(name = "idx_storm_sim_capsize", columnList = "ship_id, capsizing_probability DESC")
})
public class StormSimulation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "ship_id", nullable = false, columnDefinition = "uuid")
    private UUID shipId;

    @Column(name = "simulation_name", length = 200)
    private String simulationName;

    @Column(name = "storm_severity", nullable = false, length = 50)
    private String stormSeverity;

    @Column(name = "wave_height", nullable = false, precision = 8, scale = 2)
    private BigDecimal waveHeight;

    @Column(name = "wind_speed", nullable = false, precision = 8, scale = 2)
    private BigDecimal windSpeed;

    @Column(name = "wave_period", nullable = false, precision = 8, scale = 2)
    private BigDecimal wavePeriod;

    @Column(name = "simulation_duration_hours", nullable = false, precision = 8, scale = 2)
    private BigDecimal simulationDurationHours;

    @Column(name = "monte_carlo_iterations", nullable = false)
    private Integer monteCarloIterations;

    @Column(name = "capsizing_probability", precision = 8, scale = 6)
    private BigDecimal capsizingProbability;

    @Column(name = "max_roll_angle_experienced", precision = 8, scale = 3)
    private BigDecimal maxRollAngleExperienced;

    @Column(name = "min_gm_experienced", precision = 8, scale = 4)
    private BigDecimal minGmExperienced;

    @Column(name = "righting_arm_loss_percentage", precision = 8, scale = 4)
    private BigDecimal rightingArmLossPercentage;

    @Column(name = "weather_helm_effect", precision = 8, scale = 3)
    private BigDecimal weatherHelmEffect;

    @Column(name = "broaching_probability", precision = 8, scale = 6)
    private BigDecimal broachingProbability;

    @Column(name = "parametric_roll_risk")
    private Boolean parametricRollRisk = false;

    @Column(name = "simulation_status", nullable = false, length = 20)
    private String simulationStatus = "COMPLETED";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_details", columnDefinition = "jsonb")
    private Map<String, Object> resultDetails;

    @Column(name = "simulation_time", nullable = false)
    private LocalDateTime simulationTime;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", insertable = false, updatable = false)
    private Ship ship;

    @PrePersist
    protected void onCreate() {
        if (simulationTime == null) {
            simulationTime = LocalDateTime.now();
        }
    }
}
