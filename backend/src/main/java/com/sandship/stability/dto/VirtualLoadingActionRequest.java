package com.sandship.stability.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VirtualLoadingActionRequest {

    @NotNull(message = "会话ID不能为空")
    private UUID sessionId;

    private UUID holdId;

    private UUID cargoTypeId;

    private BigDecimal weightChange;

    @NotBlank(message = "操作类型不能为空")
    private String action;
}
