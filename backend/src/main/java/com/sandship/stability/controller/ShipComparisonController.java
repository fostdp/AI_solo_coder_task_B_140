package com.sandship.stability.controller;

import com.sandship.stability.dto.ApiResponse;
import com.sandship.stability.dto.ShipComparisonRequest;
import com.sandship.stability.dto.ShipComparisonResultDTO;
import com.sandship.stability.dto.ShipDTO;
import com.sandship.stability.ship_comparator.EraComparatorService;
import com.sandship.stability.ship_comparator.ShipComparatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/ship-comparison")
@Tag(name = "船型稳性对比", description = "多船型稳性指标对比分析接口")
public class ShipComparisonController {

    @Autowired
    private ShipComparatorService shipComparatorService;

    @Autowired
    private EraComparatorService eraComparatorService;

    @PostMapping("/compare")
    @Operation(summary = "执行多船对比", description = "对多艘船舶的稳性指标进行对比分析，生成综合排名")
    public ApiResponse<ShipComparisonResultDTO> compareShips(
            @Valid @RequestBody @Parameter(description = "对比请求参数") ShipComparisonRequest request) {
        try {
            ShipComparisonResultDTO result;
            if (eraComparatorService.isCrossEraRequest(request)) {
                result = eraComparatorService.compareAncientVsModern(request);
            } else {
                result = shipComparatorService.compareShips(request);
            }
            return ApiResponse.success("船型对比完成", result);
        } catch (Exception e) {
            return ApiResponse.error("船型对比失败: " + e.getMessage());
        }
    }

    @GetMapping("/history")
    @Operation(summary = "获取对比历史", description = "获取历史船型对比记录，按创建时间倒序排列")
    public ApiResponse<List<ShipComparisonResultDTO>> getComparisonHistory(
            @RequestParam(defaultValue = "10") @Parameter(description = "返回记录数量，0表示返回全部") int limit) {
        try {
            List<ShipComparisonResultDTO> history = shipComparatorService.getComparisonHistory(limit);
            return ApiResponse.success(history);
        } catch (Exception e) {
            return ApiResponse.error("获取对比历史失败: " + e.getMessage());
        }
    }

    @GetMapping("/ships")
    @Operation(summary = "获取可对比船舶列表", description = "获取所有可用于对比的船舶信息，按分类和船型分组返回")
    public ApiResponse<Map<String, Map<String, List<ShipDTO>>>> getAvailableShips() {
        try {
            List<ShipDTO> ships = shipComparatorService.getAvailableShips();

            Map<String, Map<String, List<ShipDTO>>> grouped = ships.stream()
                    .collect(Collectors.groupingBy(
                            ship -> ship.getShipCategory() != null ? ship.getShipCategory() : "UNKNOWN",
                            Collectors.groupingBy(
                                    ship -> ship.getShipFamily() != null ? ship.getShipFamily() : "其他"
                            )
                    ));

            return ApiResponse.success(grouped);
        } catch (Exception e) {
            return ApiResponse.error("获取船舶列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取对比详情", description = "根据ID获取指定的船型对比记录详情")
    public ApiResponse<ShipComparisonResultDTO> getComparisonById(
            @PathVariable @Parameter(description = "对比记录ID") UUID id) {
        try {
            ShipComparisonResultDTO result = shipComparatorService.getComparisonById(id);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("获取对比详情失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除对比记录", description = "根据ID删除指定的船型对比记录")
    public ApiResponse<Void> deleteComparison(
            @PathVariable @Parameter(description = "对比记录ID") UUID id) {
        try {
            shipComparatorService.deleteComparison(id);
            return ApiResponse.success("对比记录已删除", null);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(e.getMessage());
        } catch (Exception e) {
            return ApiResponse.error("删除对比记录失败: " + e.getMessage());
        }
    }
}
