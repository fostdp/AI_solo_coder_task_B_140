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
public class StormSimulationResultDTO {

    private UUID id;
    private UUID shipId;
    private String shipName;
    private String simulationName;
    private String stormSeverity;
    private BigDecimal waveHeight;
    private BigDecimal windSpeed;
    private BigDecimal wavePeriod;
    private BigDecimal simulationDurationHours;
    private Integer monteCarloIterations;
    private BigDecimal capsizingProbability;
    private BigDecimal maxRollAngleExperienced;
    private BigDecimal minGmExperienced;
    private BigDecimal rightingArmLossPercentage;
    private BigDecimal weatherHelmEffect;
    private BigDecimal broachingProbability;
    private Boolean parametricRollRisk;
    private String simulationStatus;
    private Map<String, Object> resultDetails;
    private LocalDateTime simulationTime;
    private LocalDateTime createdAt;
    private List<Map<String, Object>> rollAngleTimeSeries;
    private List<Map<String, Object>> gmTimeSeries;
}
