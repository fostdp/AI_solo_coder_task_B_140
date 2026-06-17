package com.sandship.stability.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VirtualLoadingResultDTO {

    private UUID id;
    private UUID shipId;
    private String shipName;
    private String sessionName;
    private String userId;
    private Boolean isPublic;
    private Map<String, Map<String, BigDecimal>> loadingConfig;
    private BigDecimal currentGm;
    private String stabilityStatus;
    private BigDecimal totalCargoWeight;
    private BigDecimal totalCargoVolume;
    private Integer stepsTaken;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime lastActivity;

    private StabilityResultDTO stabilityResult;
    private List<CargoLoadingDTO> loadingDetails;
    private List<CargoHoldDTO> holdInfo;
    private String message;
}
