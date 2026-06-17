package com.sandship.stability.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CargoHoldDTO {

    private UUID id;
    private UUID shipId;
    private Integer holdNumber;
    private String holdName;
    private BigDecimal capacityCubic;
    private BigDecimal maxWeight;
    private BigDecimal centerGravityX;
    private BigDecimal centerGravityY;
    private BigDecimal centerGravityZ;
    private Boolean isTank;
    private BigDecimal currentWeight;
    private BigDecimal currentVolume;
    private BigDecimal weightUtilizationRate;
    private BigDecimal volumeUtilizationRate;
}
