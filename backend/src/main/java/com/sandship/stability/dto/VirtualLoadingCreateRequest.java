package com.sandship.stability.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VirtualLoadingCreateRequest {

    @NotNull(message = "船舶ID不能为空")
    private UUID shipId;

    @NotBlank(message = "会话名称不能为空")
    private String sessionName;

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    private boolean isPublic = true;
}
