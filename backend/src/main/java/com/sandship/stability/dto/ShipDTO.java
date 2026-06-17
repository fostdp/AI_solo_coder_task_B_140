package com.sandship.stability.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipDTO {

    private UUID id;
    private String name;
    private String shipType;
    private String shipCategory;
    private String shipFamily;
    private String shipVariant;
    private BigDecimal lengthOverall;
    private BigDecimal breadthMolded;
    private BigDecimal depthMolded;
    private BigDecimal designDraft;
    private BigDecimal displacement;
    private BigDecimal deadweightTons;
    private BigDecimal metacentricHeightDesign;
    private BigDecimal bowHeight;
    private String historicalPeriod;
}
