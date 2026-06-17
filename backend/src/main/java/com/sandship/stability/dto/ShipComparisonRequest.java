package com.sandship.stability.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShipComparisonRequest {

    @Size(max = 200, message = "对比名称长度不能超过200字符")
    private String comparisonName;

    @NotEmpty(message = "船舶ID列表不能为空")
    @Size(min = 2, message = "至少需要选择2艘船舶进行对比")
    private List<UUID> shipIds;

    @NotEmpty(message = "对比指标不能为空")
    private List<@NotBlank String> comparisonCriteria;

    @NotBlank(message = "装载工况不能为空")
    private String loadingCondition;

    @NotNull(message = "参考波高不能为空")
    private BigDecimal referenceWaveHeight;

    @Size(max = 100, message = "创建人长度不能超过100字符")
    private String createdBy;
}
