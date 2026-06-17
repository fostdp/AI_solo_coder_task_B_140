package com.sandship.stability.controller;

import com.sandship.stability.dto.ApiResponse;
import com.sandship.stability.dto.StormSimulationRequest;
import com.sandship.stability.dto.StormSimulationResultDTO;
import com.sandship.stability.storm_simulator.StormSimulatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/storm-simulation")
@Tag(name = "风暴倾覆模拟", description = "风暴下船舶倾覆概率模拟与分析接口")
public class StormSimulationController {

    @Autowired
    private StormSimulatorService stormSimulatorService;

    @PostMapping("/simulate/{shipId}")
    @Operation(summary = "触发单船风暴模拟", description = "对指定船舶进行风暴倾覆概率蒙特卡洛模拟")
    public ApiResponse<StormSimulationResultDTO> simulateStorm(
            @PathVariable @Parameter(description = "船舶ID") UUID shipId,
            @Valid @RequestBody StormSimulationRequest request) {
        try {
            StormSimulationResultDTO result = stormSimulatorService.simulateStorm(shipId, request);
            return ApiResponse.success("风暴模拟完成", result);
        } catch (Exception e) {
            return ApiResponse.error("风暴模拟失败: " + e.getMessage());
        }
    }

    @PostMapping("/batch-simulate")
    @Operation(summary = "批量模拟多船", description = "对多艘船舶同时进行风暴倾覆模拟")
    public ApiResponse<List<StormSimulationResultDTO>> batchSimulate(
            @Valid @RequestBody StormSimulationRequest request) {
        try {
            if (request.getShipIds() == null || request.getShipIds().isEmpty()) {
                return ApiResponse.error("请指定至少一艘船舶进行模拟");
            }

            List<StormSimulationResultDTO> results = new ArrayList<>();
            for (UUID shipId : request.getShipIds()) {
                StormSimulationResultDTO result = stormSimulatorService.simulateStorm(shipId, request);
                results.add(result);
            }

            return ApiResponse.success(String.format("批量模拟完成，共 %d 艘船舶", results.size()), results);
        } catch (Exception e) {
            return ApiResponse.error("批量模拟失败: " + e.getMessage());
        }
    }

    @GetMapping("/ship/{shipId}/latest")
    @Operation(summary = "获取船舶最新模拟结果", description = "获取指定船舶最新的风暴模拟结果")
    public ApiResponse<StormSimulationResultDTO> getLatestSimulation(
            @PathVariable @Parameter(description = "船舶ID") UUID shipId) {
        Optional<StormSimulationResultDTO> result = stormSimulatorService.getLatestSimulation(shipId);
        return result.map(r -> ApiResponse.success(r))
                .orElse(ApiResponse.error("暂无风暴模拟数据"));
    }

    @GetMapping("/ship/{shipId}/history")
    @Operation(summary = "获取船舶模拟历史", description = "获取指定船舶的所有风暴模拟历史记录")
    public ApiResponse<List<StormSimulationResultDTO>> getSimulationHistory(
            @PathVariable @Parameter(description = "船舶ID") UUID shipId,
            @RequestParam(defaultValue = "0") @Parameter(description = "返回条数限制，0表示不限制") int limit) {
        List<StormSimulationResultDTO> results = stormSimulatorService.getSimulationHistory(shipId, limit);
        return ApiResponse.success(results);
    }

    @GetMapping("/public")
    @Operation(summary = "获取公开模拟结果", description = "获取最近的公开风暴模拟结果（最多10条）")
    public ApiResponse<List<StormSimulationResultDTO>> getPublicSimulations() {
        List<StormSimulationResultDTO> results = stormSimulatorService.getPublicSimulations();
        return ApiResponse.success(results);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取模拟详情", description = "根据ID获取风暴模拟的详细结果")
    public ApiResponse<StormSimulationResultDTO> getSimulationById(
            @PathVariable @Parameter(description = "模拟记录ID") UUID id) {
        Optional<StormSimulationResultDTO> result = stormSimulatorService.getSimulationById(id);
        return result.map(r -> ApiResponse.success(r))
                .orElse(ApiResponse.error("模拟记录不存在"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除模拟记录", description = "删除指定的风暴模拟记录")
    public ApiResponse<Boolean> deleteSimulation(
            @PathVariable @Parameter(description = "模拟记录ID") UUID id) {
        boolean deleted = stormSimulatorService.deleteSimulation(id);
        if (deleted) {
            return ApiResponse.success("删除成功", true);
        } else {
            return ApiResponse.error("模拟记录不存在");
        }
    }
}
