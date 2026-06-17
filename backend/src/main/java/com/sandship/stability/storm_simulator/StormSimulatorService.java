package com.sandship.stability.storm_simulator;

import com.sandship.stability.dto.StormSimulationRequest;
import com.sandship.stability.dto.StormSimulationResultDTO;
import com.sandship.stability.entity.Ship;
import com.sandship.stability.entity.StormSimulation;
import com.sandship.stability.repository.ShipRepository;
import com.sandship.stability.repository.StormSimulationRepository;
import com.sandship.stability.storm_simulator.monte_carlo.MonteCarloEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StormSimulatorService {

    @Autowired
    @Qualifier("stabilityExecutor")
    private Executor stabilityExecutor;

    @Autowired
    @Qualifier("stormSimExecutor")
    private Executor stormSimExecutor;

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private StormSimulationRepository stormSimulationRepository;

    @Transactional
    public StormSimulationResultDTO simulateStorm(UUID shipId, StormSimulationRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("[StormSimulator] 开始风暴倾覆模拟 - 船舶: {}, 风暴强度: {}, 波高: {}m",
                shipId, request.getStormSeverity(), request.getWaveHeight());

        try {
            Optional<Ship> shipOpt = shipRepository.findById(shipId);
            if (shipOpt.isEmpty()) {
                log.error("[StormSimulator] 船舶不存在: {}", shipId);
                throw new IllegalArgumentException("船舶不存在: " + shipId);
            }

            Ship ship = shipOpt.get();
            BigDecimal baseGM = ship.getMetacentricHeightDesign();

            MonteCarloEngine.ShipParams shipParams = new MonteCarloEngine.ShipParams(
                    ship.getLengthOverall().doubleValue(),
                    ship.getBreadthMolded().doubleValue(),
                    ship.getDisplacement().doubleValue(),
                    baseGM.doubleValue(),
                    ship.getBowHeight().doubleValue(),
                    ship.getRollRadiusCoefficient() != null
                            ? ship.getRollRadiusCoefficient().doubleValue()
                            : 0.35
            );

            MonteCarloEngine.StormParams stormParams = new MonteCarloEngine.StormParams(
                    request.getWaveHeight().doubleValue(),
                    request.getWavePeriod().doubleValue(),
                    request.getWindSpeed().doubleValue(),
                    request.getStormSeverity()
            );

            int iterations = request.getMonteCarloIterations() != null
                    ? request.getMonteCarloIterations() : 10000;

            MonteCarloEngine.SimulationResult simResult = MonteCarloEngine.runSimulation(shipParams, stormParams, iterations);

            int durationMinutes = request.getSimulationDurationHours() != null
                    ? request.getSimulationDurationHours().multiply(new BigDecimal("60")).intValue()
                    : 24 * 60;

            List<Map<String, Object>> rollAngleTimeSeries = MonteCarloEngine.generateRollAngleTimeSeries(
                    durationMinutes, simResult.rollStdDev, simResult.parametricAmplification,
                    request.getWindSpeed().doubleValue(), shipParams, baseGM.doubleValue());

            List<Map<String, Object>> gmTimeSeries = MonteCarloEngine.generateGMTimeSeries(
                    durationMinutes, baseGM.doubleValue(), rollAngleTimeSeries);

            StormSimulation simulation = new StormSimulation();
            simulation.setShipId(shipId);
            simulation.setSimulationName(generateSimulationName(ship, request));
            simulation.setStormSeverity(request.getStormSeverity());
            simulation.setWaveHeight(request.getWaveHeight());
            simulation.setWindSpeed(request.getWindSpeed());
            simulation.setWavePeriod(request.getWavePeriod());
            simulation.setSimulationDurationHours(request.getSimulationDurationHours() != null
                    ? request.getSimulationDurationHours() : new BigDecimal("24.0"));
            simulation.setMonteCarloIterations(iterations);
            simulation.setCapsizingProbability(BigDecimal.valueOf(simResult.capsizingProbability).setScale(6, RoundingMode.HALF_UP));
            simulation.setMaxRollAngleExperienced(BigDecimal.valueOf(simResult.maxRollAngle).setScale(3, RoundingMode.HALF_UP));
            simulation.setMinGmExperienced(BigDecimal.valueOf(simResult.minGM).setScale(4, RoundingMode.HALF_UP));
            simulation.setRightingArmLossPercentage(BigDecimal.valueOf(simResult.rightingArmLossPercentage).setScale(4, RoundingMode.HALF_UP));
            simulation.setWeatherHelmEffect(BigDecimal.valueOf(simResult.weatherHelmEffect).setScale(3, RoundingMode.HALF_UP));
            simulation.setBroachingProbability(BigDecimal.valueOf(simResult.broachingProbability).setScale(6, RoundingMode.HALF_UP));
            simulation.setParametricRollRisk(simResult.parametricRollRisk);
            simulation.setSimulationStatus("COMPLETED");
            simulation.setSimulationTime(LocalDateTime.now());
            simulation.setResultDetails(buildResultDetails(ship, request, simResult));

            simulation = stormSimulationRepository.save(simulation);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[StormSimulator] 模拟完成 - 船舶: {}, 倾覆概率: {:.4f}%, 耗时: {}ms",
                    ship.getName(), simResult.capsizingProbability * 100, duration);

            return convertToDTO(simulation, ship.getName(), rollAngleTimeSeries, gmTimeSeries);

        } catch (Exception e) {
            log.error("[StormSimulator] 模拟失败 - 船舶: {}", shipId, e);
            throw new RuntimeException("风暴模拟失败: " + e.getMessage(), e);
        }
    }

    @Transactional
    public List<StormSimulationResultDTO> simulateBatch(StormSimulationRequest request) {
        if (request.getShipIds() == null || request.getShipIds().isEmpty()) {
            throw new IllegalArgumentException("请指定至少一艘船舶进行批量模拟");
        }

        log.info("[StormSimulator] 开始批量风暴模拟 - 船舶数量: {}", request.getShipIds().size());
        long startTime = System.currentTimeMillis();

        List<CompletableFuture<StormSimulationResultDTO>> futures = request.getShipIds().stream()
                .map(shipId -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return simulateStorm(shipId, request);
                    } catch (Exception e) {
                        log.error("[StormSimulator] 批量模拟单船失败 - 船舶: {}", shipId, e);
                        return null;
                    }
                }, stormSimExecutor))
                .collect(Collectors.toList());

        List<StormSimulationResultDTO> results = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        long duration = System.currentTimeMillis() - startTime;
        log.info("[StormSimulator] 批量模拟完成 - 成功: {}/{}, 耗时: {}ms",
                results.size(), request.getShipIds().size(), duration);

        return results;
    }

    public List<StormSimulationResultDTO> getSimulationHistory(UUID shipId, int limit) {
        log.info("[StormSimulator] 获取船舶模拟历史 - 船舶: {}, 限制: {}", shipId, limit);

        List<StormSimulation> simulations = stormSimulationRepository.findByShipIdOrderBySimulationTimeDesc(shipId);

        if (limit > 0 && simulations.size() > limit) {
            simulations = simulations.subList(0, limit);
        }

        Map<UUID, String> shipNameCache = new HashMap<>();
        shipRepository.findById(shipId).ifPresent(ship -> shipNameCache.put(shipId, ship.getName()));

        return simulations.stream()
                .map(sim -> convertToDTO(sim, shipNameCache.get(sim.getShipId()), null, null))
                .collect(Collectors.toList());
    }

    public List<StormSimulationResultDTO> getPublicSimulations() {
        log.info("[StormSimulator] 获取公开模拟结果");

        List<StormSimulation> simulations = stormSimulationRepository.findTop10ByOrderBySimulationTimeDesc();

        Set<UUID> shipIds = simulations.stream()
                .map(StormSimulation::getShipId)
                .collect(Collectors.toSet());

        Map<UUID, String> shipNameCache = new HashMap<>();
        for (UUID id : shipIds) {
            shipRepository.findById(id).ifPresent(ship -> shipNameCache.put(id, ship.getName()));
        }

        return simulations.stream()
                .map(sim -> convertToDTO(sim, shipNameCache.get(sim.getShipId()), null, null))
                .collect(Collectors.toList());
    }

    public Optional<StormSimulationResultDTO> getSimulationById(UUID id) {
        return stormSimulationRepository.findById(id)
                .map(sim -> {
                    String shipName = shipRepository.findById(sim.getShipId())
                            .map(Ship::getName)
                            .orElse(null);
                    return convertToDTO(sim, shipName, null, null);
                });
    }

    public Optional<StormSimulationResultDTO> getLatestSimulation(UUID shipId) {
        return stormSimulationRepository.findTopByShipIdOrderBySimulationTimeDesc(shipId)
                .map(sim -> {
                    String shipName = shipRepository.findById(sim.getShipId())
                            .map(Ship::getName)
                            .orElse(null);
                    return convertToDTO(sim, shipName, null, null);
                });
    }

    @Transactional
    public boolean deleteSimulation(UUID id) {
        if (stormSimulationRepository.existsById(id)) {
            stormSimulationRepository.deleteById(id);
            log.info("[StormSimulator] 删除模拟记录: {}", id);
            return true;
        }
        return false;
    }

    public Map<String, Object> getStatistics(UUID shipId) {
        log.info("[StormSimulator] 获取船舶风暴模拟统计 - 船舶: {}", shipId);

        List<StormSimulation> simulations = stormSimulationRepository.findByShipIdOrderBySimulationTimeDesc(shipId);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalSimulations", simulations.size());

        if (simulations.isEmpty()) {
            stats.put("averageCapsizingProbability", BigDecimal.ZERO);
            stats.put("maxCapsizingProbability", BigDecimal.ZERO);
            stats.put("minCapsizingProbability", BigDecimal.ZERO);
            stats.put("parametricRollRiskCount", 0);
            stats.put("simulationTimeRange", null);
            return stats;
        }

        BigDecimal avgCapsizingProb = simulations.stream()
                .map(StormSimulation::getCapsizingProbability)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(simulations.size()), 6, RoundingMode.HALF_UP);

        BigDecimal maxCapsizingProb = simulations.stream()
                .map(StormSimulation::getCapsizingProbability)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal minCapsizingProb = simulations.stream()
                .map(StormSimulation::getCapsizingProbability)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        long parametricRollCount = simulations.stream()
                .map(StormSimulation::getParametricRollRisk)
                .filter(Boolean.TRUE::equals)
                .count();

        LocalDateTime earliest = simulations.stream()
                .map(StormSimulation::getSimulationTime)
                .filter(Objects::nonNull)
                .min(LocalDateTime::compareTo)
                .orElse(null);

        LocalDateTime latest = simulations.stream()
                .map(StormSimulation::getSimulationTime)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        Map<String, LocalDateTime> timeRange = new LinkedHashMap<>();
        timeRange.put("earliest", earliest);
        timeRange.put("latest", latest);

        stats.put("averageCapsizingProbability", avgCapsizingProb);
        stats.put("maxCapsizingProbability", maxCapsizingProb);
        stats.put("minCapsizingProbability", minCapsizingProb);
        stats.put("parametricRollRiskCount", parametricRollCount);
        stats.put("simulationTimeRange", timeRange);

        return stats;
    }

    private String generateSimulationName(Ship ship, StormSimulationRequest request) {
        String severity = request.getStormSeverity();
        String severityCn = switch (severity) {
            case "TROPICAL_STORM" -> "热带风暴";
            case "SEVERE_STORM" -> "强风暴";
            case "TYPHOON" -> "台风";
            case "HURRICANE" -> "飓风";
            default -> severity;
        };
        return String.format("%s - %s模拟 - %s",
                ship.getName(), severityCn, LocalDateTime.now().toString().substring(0, 16));
    }

    private Map<String, Object> buildResultDetails(Ship ship, StormSimulationRequest request,
                                                    MonteCarloEngine.SimulationResult simResult) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("shipName", ship.getName());
        details.put("shipType", ship.getShipType());
        details.put("loadingCondition", request.getLoadingCondition());
        details.put("periodRatio", round(simResult.periodRatio, 4));
        details.put("capsizingCount", simResult.capsizingCount);
        details.put("totalIterations", simResult.totalIterations);
        details.put("designGM", ship.getMetacentricHeightDesign().doubleValue());
        details.put("shipDisplacement", ship.getDisplacement().doubleValue());
        details.put("breadthMolded", ship.getBreadthMolded().doubleValue());
        details.put("lengthOverall", ship.getLengthOverall().doubleValue());
        return details;
    }

    private StormSimulationResultDTO convertToDTO(StormSimulation simulation, String shipName,
                                                   List<Map<String, Object>> rollAngleTimeSeries,
                                                   List<Map<String, Object>> gmTimeSeries) {
        StormSimulationResultDTO dto = new StormSimulationResultDTO();
        dto.setId(simulation.getId());
        dto.setShipId(simulation.getShipId());
        dto.setShipName(shipName);
        dto.setSimulationName(simulation.getSimulationName());
        dto.setStormSeverity(simulation.getStormSeverity());
        dto.setWaveHeight(simulation.getWaveHeight());
        dto.setWindSpeed(simulation.getWindSpeed());
        dto.setWavePeriod(simulation.getWavePeriod());
        dto.setSimulationDurationHours(simulation.getSimulationDurationHours());
        dto.setMonteCarloIterations(simulation.getMonteCarloIterations());
        dto.setCapsizingProbability(simulation.getCapsizingProbability());
        dto.setMaxRollAngleExperienced(simulation.getMaxRollAngleExperienced());
        dto.setMinGmExperienced(simulation.getMinGmExperienced());
        dto.setRightingArmLossPercentage(simulation.getRightingArmLossPercentage());
        dto.setWeatherHelmEffect(simulation.getWeatherHelmEffect());
        dto.setBroachingProbability(simulation.getBroachingProbability());
        dto.setParametricRollRisk(simulation.getParametricRollRisk());
        dto.setSimulationStatus(simulation.getSimulationStatus());
        dto.setResultDetails(simulation.getResultDetails());
        dto.setSimulationTime(simulation.getSimulationTime());
        dto.setCreatedAt(simulation.getCreatedAt());
        dto.setRollAngleTimeSeries(rollAngleTimeSeries);
        dto.setGmTimeSeries(gmTimeSeries);
        return dto;
    }

    private double round(double value, int places) {
        BigDecimal bd = BigDecimal.valueOf(value);
        return bd.setScale(places, RoundingMode.HALF_UP).doubleValue();
    }
}
