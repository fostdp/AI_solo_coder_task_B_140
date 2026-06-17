package com.sandship.stability.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StormSimulationRequest {

    private List<UUID> shipIds;

    @NotBlank(message = "风暴强度不能为空")
    @Pattern(regexp = "^(TROPICAL_STORM|SEVERE_STORM|TYPHOON|HURRICANE)$", message = "风暴强度必须是 TROPICAL_STORM, SEVERE_STORM, TYPHOON, HURRICANE 之一")
    private String stormSeverity;

    @NotNull(message = "波高不能为空")
    @DecimalMin(value = "0.1", message = "波高必须大于0.1米")
    @DecimalMax(value = "20.0", message = "波高不能超过20米")
    private BigDecimal waveHeight;

    @NotNull(message = "风速不能为空")
    @DecimalMin(value = "0.0", message = "风速不能小于0")
    @DecimalMax(value = "150.0", message = "风速不能超过150节")
    private BigDecimal windSpeed;

    @NotNull(message = "波浪周期不能为空")
    @DecimalMin(value = "1.0", message = "波浪周期必须大于1秒")
    @DecimalMax(value = "30.0", message = "波浪周期不能超过30秒")
    private BigDecimal wavePeriod;

    @DecimalMin(value = "1.0", message = "模拟时长至少1小时")
    @DecimalMax(value = "168.0", message = "模拟时长不能超过168小时")
    private BigDecimal simulationDurationHours = new BigDecimal("24.0");

    @Min(value = 1000, message = "蒙特卡洛迭代次数至少1000次")
    @Max(value = 100000, message = "蒙特卡洛迭代次数不能超过100000次")
    private Integer monteCarloIterations = 10000;

    @Pattern(regexp = "^(BALLAST|HALF_LOAD|FULL_LOAD)$", message = "装载状态必须是 BALLAST, HALF_LOAD, FULL_LOAD 之一")
    private String loadingCondition = "FULL_LOAD";
}
