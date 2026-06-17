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
@Table(name = "virtual_loading_sessions", indexes = {
    @Index(name = "idx_virtual_loading_ship", columnList = "ship_id, created_at DESC"),
    @Index(name = "idx_virtual_loading_public", columnList = "is_public", unique = false)
})
public class VirtualLoadingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false)
    private UUID id;

    @Column(name = "ship_id", nullable = false, columnDefinition = "uuid")
    private UUID shipId;

    @Column(name = "session_name", length = 200)
    private String sessionName;

    @Column(name = "user_id", length = 100)
    private String userId;

    @Column(name = "is_public")
    private Boolean isPublic = true;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "loading_config", nullable = false, columnDefinition = "jsonb")
    private Map<String, Map<String, BigDecimal>> loadingConfig;

    @Column(name = "current_gm", precision = 8, scale = 4)
    private BigDecimal currentGm;

    @Column(name = "stability_status", length = 20)
    private String stabilityStatus;

    @Column(name = "total_cargo_weight", precision = 12, scale = 2)
    private BigDecimal totalCargoWeight;

    @Column(name = "total_cargo_volume", precision = 12, scale = 2)
    private BigDecimal totalCargoVolume;

    @Column(name = "steps_taken")
    private Integer stepsTaken = 0;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_activity")
    private LocalDateTime lastActivity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ship_id", insertable = false, updatable = false)
    private Ship ship;

    @PrePersist
    protected void onCreate() {
        if (lastActivity == null) {
            lastActivity = LocalDateTime.now();
        }
    }
}
