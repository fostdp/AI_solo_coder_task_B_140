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
public class ShipComparisonResultDTO {

    private UUID id;
    private String comparisonName;
    private List<UUID> shipIds;
    private List<String> comparisonCriteria;
    private String loadingCondition;
    private BigDecimal referenceWaveHeight;
    private String createdBy;
    private LocalDateTime createdAt;
    private String rankingSummary;

    private List<ShipComparisonItem> comparisonItems;
    private List<String> rankingSummaryList;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShipComparisonItem {
        private UUID shipId;
        private String shipName;
        private String shipType;
        private String shipFamily;
        private String category;
        private Map<String, BigDecimal> metrics;
        private Integer rank;
        private BigDecimal score;
    }
}
