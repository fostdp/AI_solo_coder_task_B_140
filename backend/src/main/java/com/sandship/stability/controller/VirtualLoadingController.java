package com.sandship.stability.controller;

import com.sandship.stability.dto.*;
import com.sandship.stability.vr_loading.VRLoadingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/virtual-loading")
@Tag(name = "虚拟装载体验", description = "虚拟装载体验模块接口")
public class VirtualLoadingController {

    @Autowired
    private VRLoadingService vrLoadingService;

    @PostMapping("/session")
    @Operation(summary = "创建新的虚拟装载会话")
    public ApiResponse<VirtualLoadingResultDTO> createSession(
            @Valid @RequestBody VirtualLoadingCreateRequest request) {
        try {
            VirtualLoadingResultDTO result = vrLoadingService.createSession(request);
            return ApiResponse.success("会话创建成功", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("创建会话失败: " + e.getMessage());
        }
    }

    @PostMapping("/action")
    @Operation(summary = "执行装载/卸载动作")
    public ApiResponse<VirtualLoadingResultDTO> executeAction(
            @Valid @RequestBody VirtualLoadingActionRequest request) {
        try {
            VirtualLoadingResultDTO result = vrLoadingService.executeAction(request);
            return ApiResponse.success(result.getMessage(), result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("执行操作失败: " + e.getMessage());
        }
    }

    @GetMapping("/session/{id}")
    @Operation(summary = "获取会话详情")
    public ApiResponse<VirtualLoadingResultDTO> getSession(
            @Parameter(description = "会话ID") @PathVariable UUID id) {
        try {
            VirtualLoadingResultDTO result = vrLoadingService.getSession(id);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("获取会话失败: " + e.getMessage());
        }
    }

    @GetMapping("/ship/{shipId}/active")
    @Operation(summary = "获取船舶的活跃会话列表")
    public ApiResponse<List<VirtualLoadingResultDTO>> getActiveSessions(
            @Parameter(description = "船舶ID") @PathVariable UUID shipId) {
        try {
            List<VirtualLoadingResultDTO> results = vrLoadingService.getActiveSessions(shipId);
            return ApiResponse.success(results);
        } catch (Exception e) {
            return ApiResponse.error("获取活跃会话失败: " + e.getMessage());
        }
    }

    @GetMapping("/public")
    @Operation(summary = "分页获取公开会话列表")
    public ApiResponse<Page<VirtualLoadingResultDTO>> getPublicSessions(
            @Parameter(description = "页码，从0开始") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        try {
            Page<VirtualLoadingResultDTO> results = vrLoadingService.getPublicSessions(page, size);
            return ApiResponse.success(results);
        } catch (Exception e) {
            return ApiResponse.error("获取公开会话失败: " + e.getMessage());
        }
    }

    @GetMapping("/user/{userId}")
    @Operation(summary = "获取用户的会话列表")
    public ApiResponse<List<VirtualLoadingResultDTO>> getUserSessions(
            @Parameter(description = "用户ID") @PathVariable String userId) {
        try {
            List<VirtualLoadingResultDTO> results = vrLoadingService.getUserSessions(userId);
            return ApiResponse.success(results);
        } catch (Exception e) {
            return ApiResponse.error("获取用户会话失败: " + e.getMessage());
        }
    }

    @PostMapping("/session/{id}/clone")
    @Operation(summary = "克隆会话")
    public ApiResponse<VirtualLoadingResultDTO> cloneSession(
            @Parameter(description = "源会话ID") @PathVariable UUID id,
            @Parameter(description = "新会话名称") @RequestParam String newName) {
        try {
            VirtualLoadingResultDTO result = vrLoadingService.cloneSession(id, newName);
            return ApiResponse.success("会话克隆成功", result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("克隆会话失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/session/{id}")
    @Operation(summary = "关闭/删除会话")
    public ApiResponse<Void> closeSession(
            @Parameter(description = "会话ID") @PathVariable UUID id) {
        try {
            vrLoadingService.closeSession(id);
            return ApiResponse.success("会话已关闭", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("关闭会话失败: " + e.getMessage());
        }
    }
}
