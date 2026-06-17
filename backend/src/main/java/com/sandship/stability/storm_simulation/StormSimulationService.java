package com.sandship.stability.storm_simulation;

import com.sandship.stability.dto.StormSimulationRequest;
import com.sandship.stability.dto.StormSimulationResultDTO;
import com.sandship.stability.entity.Ship;
import com.sandship.stability.entity.StormSimulation;
import com.sandship.stability.repository.ShipRepository;
import com.sandship.stability.repository.StormSimulationRepository;
import com.sandship.stability.stability_simulator.StabilitySimulatorService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.FastMath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class StormSimulationService {

    private static final double GRAVITY = 9.81;
    private static final double DEFAULT_ROLL_RADIUS_COEFFICIENT = 0.35;
    private static final double CAPSIZING_ROLL_THRESHOLD = 50.0;
    private static final double CAPSIZING_GM_THRESHOLD = 0.05;
    private static final int DEFAULT_TIME_STEP_MINUTES = 1;

    @Autowired
    private StabilitySimulatorService stabilitySimulatorService;

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private StormSimulationRepository stormSimulationRepository;

    @Transactional
    public StormSimulationResultDTO simulateStorm(UUID shipId, StormSimulationRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("[Storm Simulation] 开始风暴倾覆模拟 - 船舶: {}, 风暴强度: {}, 波高: {}m",
                shipId, request.getStormSeverity(), request.getWaveHeight());

        try {
            Optional<Ship> shipOpt = shipRepository.findById(shipId);
            if (shipOpt.isEmpty()) {
                log.error("[Storm Simulation] 船舶不存在: {}", shipId);
                throw new IllegalArgumentException("船舶不存在: " + shipId);
            }

            Ship ship = shipOpt.get();
            BigDecimal baseGM = ship.getMetacentricHeightDesign();
            BigDecimal rollPeriod = calculateRollPeriod(ship, baseGM);

            double waveHeight = request.getWaveHeight().doubleValue();
            double wavePeriod = request.getWavePeriod().doubleValue();
            double windSpeed = request.getWindSpeed().doubleValue();
            int iterations = request.getMonteCarloIterations() != null
                    ? request.getMonteCarloIterations() : 10000;

            double rollStdDev = waveHeight * 3.5;

            double periodRatio = wavePeriod / rollPeriod.doubleValue();
            boolean parametricRollRisk = periodRatio > 0.8 && periodRatio < 1.2;
            double parametricAmplification = parametricRollRisk ? 1.8 : 1.0;

            int capsizingCount = 0;
            double maxRollAngle = 0.0;
            double minGM = baseGM.doubleValue();

            Random random = new Random();

            for (int i = 0; i < iterations; i++) {
                double rollAngle = random.nextGaussian() * rollStdDev * parametricAmplification;

                double windHeelMoment = windSpeed * windSpeed * 0.001 * ship.getBreadthMolded().doubleValue();
                double windHeelAngle = windHeelMoment / (baseGM.doubleValue() * ship.getDisplacement().doubleValue() * GRAVITY);
                rollAngle += windHeelAngle;

                double dynamicGM = baseGM.doubleValue() * (1.0 - FastMath.abs(rollAngle) / 90.0 * 0.7);
                dynamicGM = Math.max(dynamicGM, -0.5);

                if (FastMath.abs(rollAngle) > maxRollAngle) {
                    maxRollAngle = FastMath.abs(rollAngle);
                }
                if (dynamicGM < minGM) {
                    minGM = dynamicGM;
                }

                if (FastMath.abs(rollAngle) > CAPSIZING_ROLL_THRESHOLD && dynamicGM < CAPSIZING_GM_THRESHOLD) {
                    capsizingCount++;
                }
            }

            double capsizingProbability = (double) capsizingCount / iterations;

            double rightingArmLoss = calculateRightingArmLoss(waveHeight, parametricRollRisk);
            double weatherHelmEffect = calculateWeatherHelmEffect(windSpeed, ship);
            double broachingProbability = calculateBroachingProbability(waveHeight, wavePeriod, rollPeriod.doubleValue());

            int durationMinutes = request.getSimulationDurationHours() != null
                    ? request.getSimulationDurationHours().multiply(new BigDecimal("60")).intValue()
                    : 24 * 60;

            List<Map<String, Object>> rollAngleTimeSeries = generateRollAngleTimeSeries(
                    durationMinutes, rollStdDev, parametricAmplification, windSpeed, ship, baseGM.doubleValue());

            List<Map<String, Object>> gmTimeSeries = generateGMTimeSeries(
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
            simulation.setCapsizingProbability(BigDecimal.valueOf(capsizingProbability).setScale(6, RoundingMode.HALF_UP));
            simulation.setMaxRollAngleExperienced(BigDecimal.valueOf(maxRollAngle).setScale(3, RoundingMode.HALF_UP));
            simulation.setMinGmExperienced(BigDecimal.valueOf(minGM).setScale(4, RoundingMode.HALF_UP));
            simulation.setRightingArmLossPercentage(BigDecimal.valueOf(rightingArmLoss).setScale(4, RoundingMode.HALF_UP));
            simulation.setWeatherHelmEffect(BigDecimal.valueOf(weatherHelmEffect).setScale(3, RoundingMode.HALF_UP));
            simulation.setBroachingProbability(BigDecimal.valueOf(broachingProbability).setScale(6, RoundingMode.HALF_UP));
            simulation.setParametricRollRisk(parametricRollRisk);
            simulation.setSimulationStatus("COMPLETED");
            simulation.setSimulationTime(LocalDateTime.now());
            simulation.setResultDetails(buildResultDetails(ship, request, capsizingCount, iterations, periodRatio));

            simulation = stormSimulationRepository.save(simulation);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[Storm Simulation] 模拟完成 - 船舶: {}, 倾覆概率: {:.4f}%, 耗时: {}ms",
                    ship.getName(), capsizingProbability * 100, duration);

            return convertToDTO(simulation, ship.getName(), rollAngleTimeSeries, gmTimeSeries);

        } catch (Exception e) {
            log.error("[Storm Simulation] 模拟失败 - 船舶: {}", shipId, e);
            throw new RuntimeException("风暴模拟失败: " + e.getMessage(), e);
        }
    }

    public List<StormSimulationResultDTO> getSimulationHistory(UUID shipId, int limit) {
        log.info("[Storm Simulation] 获取船舶模拟历史 - 船舶: {}, 限制: {}", shipId, limit);

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
        log.info("[Storm Simulation] 获取公开模拟结果");

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

    @Transactional
    public boolean deleteSimulation(UUID id) {
        if (stormSimulationRepository.existsById(id)) {
            stormSimulationRepository.deleteById(id);
            log.info("[Storm Simulation] 删除模拟记录: {}", id);
            return true;
        }
        return false;
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

    private BigDecimal calculateRollPeriod(Ship ship, BigDecimal gm) {
        if (gm.doubleValue() <= 0) return BigDecimal.ZERO;

        double breadth = ship.getBreadthMolded().doubleValue();
        double kFactor = DEFAULT_ROLL_RADIUS_COEFFICIENT;
        double massRadiusOfGyration = breadth * kFactor;
        double period = 2 * Math.PI * massRadiusOfGyration /
                FastMath.sqrt(GRAVITY * gm.doubleValue());

        return BigDecimal.valueOf(period).setScale(3, RoundingMode.HALF_UP);
    }

    private double calculateRightingArmLoss(double waveHeight, boolean parametricRollRisk) {
        double baseLoss = waveHeight * 3.5;
        if (parametricRollRisk) {
            baseLoss *= 1.5;
        }
        return Math.min(baseLoss, 80.0);
    }

    private double calculateWeatherHelmEffect(double windSpeed, Ship ship) {
        double breadth = ship.getBreadthMolded().doubleValue();
        double length = ship.getLengthOverall().doubleValue();
        return (windSpeed * windSpeed * breadth * 0.0005) / (length * 0.1);
    }

    private double calculateBroachingProbability(double waveHeight, double wavePeriod, double rollPeriod) {
        if (waveHeight < 3.0) return 0.0;

        double periodMatch = 1.0 - FastMath.abs(wavePeriod - rollPeriod) / FastMath.max(wavePeriod, rollPeriod);
        double heightFactor = (waveHeight - 3.0) / 10.0;

        return Math.min(periodMatch * heightFactor * 0.5, 0.8);
    }

    private List<Map<String, Object>> generateRollAngleTimeSeries(
            int durationMinutes, double rollStdDev, double parametricAmplification,
            double windSpeed, Ship ship, double baseGM) {

        List<Map<String, Object>> timeSeries = new ArrayList<>();
        Random random = new Random();

        double currentRoll = 0.0;
        double windHeelMoment = windSpeed * windSpeed * 0.001 * ship.getBreadthMolded().doubleValue();
        double windHeelAngle = windHeelMoment / (baseGM * ship.getDisplacement().doubleValue() * GRAVITY);

        for (int minute = 0; minute < durationMinutes; minute += DEFAULT_TIME_STEP_MINUTES) {
            double randomComponent = random.nextGaussian() * rollStdDev * parametricAmplification * 0.3;
            double waveComponent = FastMath.sin(minute * 2 * Math.PI / 10.0) * rollStdDev * 0.5;

            currentRoll = currentRoll * 0.95 + randomComponent + waveComponent + windHeelAngle * 0.1;
            currentRoll = Math.max(-60.0, Math.min(60.0, currentRoll));

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", minute);
            point.put("rollAngle", round(currentRoll, 3));
            timeSeries.add(point);
        }

        return timeSeries;
    }

    private List<Map<String, Object>> generateGMTimeSeries(
            int durationMinutes, double baseGM, List<Map<String, Object>> rollAngleSeries) {

        List<Map<String, Object>> timeSeries = new ArrayList<>();

        for (int i = 0; i < rollAngleSeries.size() && i * DEFAULT_TIME_STEP_MINUTES < durationMinutes; i++) {
            Map<String, Object> rollPoint = rollAngleSeries.get(i);
            double rollAngle = ((Number) rollPoint.get("rollAngle")).doubleValue();

            double dynamicGM = baseGM * (1.0 - FastMath.abs(rollAngle) / 90.0 * 0.7);
            dynamicGM = Math.max(dynamicGM, -0.3);

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("time", i * DEFAULT_TIME_STEP_MINUTES);
            point.put("gm", round(dynamicGM, 4));
            timeSeries.add(point);
        }

        return timeSeries;
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
                                                    int capsizingCount, int iterations, double periodRatio) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("shipName", ship.getName());
        details.put("shipType", ship.getShipType());
        details.put("loadingCondition", request.getLoadingCondition());
        details.put("periodRatio", round(periodRatio, 4));
        details.put("capsizingCount", capsizingCount);
        details.put("totalIterations", iterations);
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
