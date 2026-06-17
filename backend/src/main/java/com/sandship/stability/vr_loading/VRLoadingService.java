package com.sandship.stability.vr_loading;

import com.sandship.stability.config.ShipParamsConfig;
import com.sandship.stability.config.StabilityConfig;
import com.sandship.stability.dto.*;
import com.sandship.stability.entity.*;
import com.sandship.stability.repository.*;
import com.sandship.stability.vr_loading.strategy.StrategyEngine;
import com.sandship.stability.vr_loading.validator.LoadingValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.math3.util.FastMath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
public class VRLoadingService {

    private static final double GRAVITY = 9.81;

    private final LoadingValidator validator = new LoadingValidator();
    private final StrategyEngine strategyEngine = new StrategyEngine();

    @Autowired
    @Qualifier("stabilityExecutor")
    private Executor stabilityExecutor;

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private CargoHoldRepository cargoHoldRepository;

    @Autowired
    private CargoTypeRepository cargoTypeRepository;

    @Autowired
    private VirtualLoadingSessionRepository sessionRepository;

    @Autowired
    private StabilityConfig stabilityConfig;

    @Autowired
    private ShipParamsConfig shipParamsConfig;

    @Transactional
    public VirtualLoadingResultDTO createSession(VirtualLoadingCreateRequest request) {
        log.info("[VRLoading] 创建新会话 - 船舶: {}, 用户: {}, 名称: {}",
                request.getShipId(), request.getUserId(), request.getSessionName());

        Optional<Ship> shipOpt = shipRepository.findById(request.getShipId());
        if (shipOpt.isEmpty()) {
            throw new IllegalArgumentException("船舶不存在: " + request.getShipId());
        }

        Ship ship = shipOpt.get();
        VirtualLoadingSession session = new VirtualLoadingSession();
        session.setShipId(request.getShipId());
        session.setSessionName(request.getSessionName());
        session.setUserId(request.getUserId());
        session.setIsPublic(request.isPublic());
        session.setLoadingConfig(new HashMap<>());
        session.setStepsTaken(0);
        session.setIsActive(true);
        session.setTotalCargoWeight(BigDecimal.ZERO);
        session.setTotalCargoVolume(BigDecimal.ZERO);

        StabilityResultDTO stabilityResult = calculateVirtualStabilityAsync(ship, session.getLoadingConfig()).join();
        session.setCurrentGm(stabilityResult.getGmValue());
        session.setStabilityStatus(stabilityResult.getStabilityStatus());

        session = sessionRepository.save(session);
        log.info("[VRLoading] 会话创建成功 - ID: {}", session.getId());

        return buildResultDTO(session, ship, stabilityResult, "会话创建成功");
    }

    @Transactional
    public VirtualLoadingResultDTO executeAction(VirtualLoadingActionRequest request) {
        log.info("[VRLoading] 执行操作 - 会话: {}, 操作: {}", request.getSessionId(), request.getAction());

        LoadingValidator.ValidationResult fieldResult = validator.validateRequiredFields(
                request.getSessionId(), request.getAction(), request.getHoldId(),
                request.getCargoTypeId(), request.getWeightChange());
        if (!fieldResult.isValid()) {
            throw new IllegalArgumentException(fieldResult.getMessage());
        }

        Optional<VirtualLoadingSession> sessionOpt = sessionRepository.findById(request.getSessionId());
        if (sessionOpt.isEmpty()) {
            throw new IllegalArgumentException("会话不存在: " + request.getSessionId());
        }

        VirtualLoadingSession session = sessionOpt.get();
        if (!Boolean.TRUE.equals(session.getIsActive())) {
            throw new IllegalStateException("会话已关闭，无法执行操作");
        }

        Ship ship = shipRepository.findById(session.getShipId())
                .orElseThrow(() -> new IllegalArgumentException("船舶不存在: " + session.getShipId()));

        String message = null;
        Map<String, Map<String, BigDecimal>> loadingConfig = session.getLoadingConfig();

        switch (request.getAction().toUpperCase()) {
            case "LOAD":
                message = handleLoad(session, loadingConfig, request);
                break;
            case "UNLOAD":
                message = handleUnload(session, loadingConfig, request);
                break;
            case "RESET_HOLD":
                message = handleResetHold(session, loadingConfig, request);
                break;
            case "RESET_ALL":
                message = handleResetAll(session, loadingConfig);
                break;
            default:
                throw new IllegalArgumentException("不支持的操作类型: " + request.getAction());
        }

        StabilityResultDTO stabilityResult = calculateVirtualStabilityAsync(ship, loadingConfig).join();

        session.setCurrentGm(stabilityResult.getGmValue());
        session.setStabilityStatus(stabilityResult.getStabilityStatus());
        session.setStepsTaken(session.getStepsTaken() + 1);
        session.setLastActivity(LocalDateTime.now());

        BigDecimal[] totals = calculateTotalWeightAndVolume(loadingConfig);
        session.setTotalCargoWeight(totals[0]);
        session.setTotalCargoVolume(totals[1]);

        session = sessionRepository.save(session);

        return buildResultDTO(session, ship, stabilityResult, message);
    }

    private String handleLoad(VirtualLoadingSession session, Map<String, Map<String, BigDecimal>> loadingConfig,
                              VirtualLoadingActionRequest request) {
        UUID holdId = request.getHoldId();
        UUID cargoTypeId = request.getCargoTypeId();
        BigDecimal weightChange = request.getWeightChange();

        if (weightChange.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("装载重量必须为正数");
        }

        CargoHold hold = cargoHoldRepository.findById(holdId)
                .orElseThrow(() -> new IllegalArgumentException("货舱不存在: " + holdId));
        CargoType cargoType = cargoTypeRepository.findById(cargoTypeId)
                .orElseThrow(() -> new IllegalArgumentException("货物类型不存在: " + cargoTypeId));

        BigDecimal currentWeight = getCurrentHoldWeight(loadingConfig, holdId);

        LoadingValidator.ValidationResult weightResult = validator.validateLoadWeight(
                currentWeight.doubleValue(),
                hold.getMaxWeight().doubleValue(),
                weightChange.doubleValue()
        );
        if (!weightResult.isValid()) {
            return weightResult.getMessage();
        }

        BigDecimal currentVolume = calculateCurrentVolume(loadingConfig, holdId, cargoType);
        BigDecimal addVolume = weightChange.divide(cargoType.getDensity(), 2, RoundingMode.HALF_UP);

        LoadingValidator.ValidationResult volumeResult = validator.validateLoadVolume(
                currentVolume.doubleValue(),
                hold.getCapacityCubic().doubleValue(),
                addVolume.doubleValue()
        );
        if (!volumeResult.isValid()) {
            return volumeResult.getMessage();
        }

        loadingConfig.computeIfAbsent(holdId.toString(), k -> new HashMap<>())
                .merge(cargoTypeId.toString(), weightChange, BigDecimal::add);

        return String.format("成功装载 %.2f 吨 %s 到 %s",
                weightChange, cargoType.getCargoName(), hold.getHoldName());
    }

    private String handleUnload(VirtualLoadingSession session, Map<String, Map<String, BigDecimal>> loadingConfig,
                                VirtualLoadingActionRequest request) {
        UUID holdId = request.getHoldId();
        UUID cargoTypeId = request.getCargoTypeId();
        BigDecimal weightChange = request.getWeightChange();

        if (weightChange.compareTo(BigDecimal.ZERO) >= 0) {
            throw new IllegalArgumentException("卸载重量必须为负数");
        }

        BigDecimal unloadWeight = weightChange.abs();

        CargoHold hold = cargoHoldRepository.findById(holdId)
                .orElseThrow(() -> new IllegalArgumentException("货舱不存在: " + holdId));
        CargoType cargoType = cargoTypeRepository.findById(cargoTypeId)
                .orElseThrow(() -> new IllegalArgumentException("货物类型不存在: " + cargoTypeId));

        Map<String, BigDecimal> holdConfig = loadingConfig.get(holdId.toString());
        BigDecimal currentCargoWeight = (holdConfig != null && holdConfig.containsKey(cargoTypeId.toString()))
                ? holdConfig.get(cargoTypeId.toString())
                : BigDecimal.ZERO;

        LoadingValidator.ValidationResult unloadResult = validator.validateUnloadExistence(
                currentCargoWeight.doubleValue(),
                unloadWeight.doubleValue()
        );
        if (!unloadResult.isValid()) {
            return unloadResult.getMessage();
        }

        BigDecimal newWeight = currentCargoWeight.subtract(unloadWeight);
        if (newWeight.compareTo(BigDecimal.ZERO) <= 0) {
            holdConfig.remove(cargoTypeId.toString());
            if (holdConfig.isEmpty()) {
                loadingConfig.remove(holdId.toString());
            }
        } else {
            holdConfig.put(cargoTypeId.toString(), newWeight);
        }

        return String.format("成功从 %s 卸载 %.2f 吨 %s",
                hold.getHoldName(), unloadWeight, cargoType.getCargoName());
    }

    private String handleResetHold(VirtualLoadingSession session, Map<String, Map<String, BigDecimal>> loadingConfig,
                                   VirtualLoadingActionRequest request) {
        CargoHold hold = cargoHoldRepository.findById(request.getHoldId())
                .orElseThrow(() -> new IllegalArgumentException("货舱不存在: " + request.getHoldId()));

        loadingConfig.remove(request.getHoldId().toString());
        return String.format("已重置货舱: %s", hold.getHoldName());
    }

    private String handleResetAll(VirtualLoadingSession session, Map<String, Map<String, BigDecimal>> loadingConfig) {
        loadingConfig.clear();
        return "已重置所有货舱";
    }

    private BigDecimal getCurrentHoldWeight(Map<String, Map<String, BigDecimal>> loadingConfig, UUID holdId) {
        Map<String, BigDecimal> holdConfig = loadingConfig.get(holdId.toString());
        if (holdConfig == null || holdConfig.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return holdConfig.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateCurrentVolume(Map<String, Map<String, BigDecimal>> loadingConfig,
                                              UUID holdId, CargoType cargoType) {
        BigDecimal currentWeight = getCurrentHoldWeight(loadingConfig, holdId);
        if (currentWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return currentWeight.divide(cargoType.getDensity(), 2, RoundingMode.HALF_UP);
    }

    public List<CargoLoading> convertLoadingConfigToCargoLoadings(Map<String, Map<String, BigDecimal>> config) {
        List<CargoLoading> loadings = new ArrayList<>();
        if (config == null) {
            return loadings;
        }

        int order = 1;
        for (Map.Entry<String, Map<String, BigDecimal>> holdEntry : config.entrySet()) {
            UUID holdId = UUID.fromString(holdEntry.getKey());
            for (Map.Entry<String, BigDecimal> cargoEntry : holdEntry.getValue().entrySet()) {
                UUID cargoTypeId = UUID.fromString(cargoEntry.getKey());
                BigDecimal weight = cargoEntry.getValue();

                if (weight.compareTo(BigDecimal.ZERO) > 0) {
                    CargoType cargoType = cargoTypeRepository.findById(cargoTypeId).orElse(null);
                    BigDecimal volume = cargoType != null
                            ? weight.divide(cargoType.getDensity(), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    CargoLoading loading = new CargoLoading();
                    loading.setHoldId(holdId);
                    loading.setCargoTypeId(cargoTypeId);
                    loading.setWeight(weight);
                    loading.setVolume(volume);
                    loading.setLoadingOrder(order++);
                    loadings.add(loading);
                }
            }
        }
        return loadings;
    }

    private BigDecimal[] calculateTotalWeightAndVolume(Map<String, Map<String, BigDecimal>> loadingConfig) {
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal totalVolume = BigDecimal.ZERO;

        if (loadingConfig == null) {
            return new BigDecimal[]{totalWeight, totalVolume};
        }

        for (Map.Entry<String, Map<String, BigDecimal>> holdEntry : loadingConfig.entrySet()) {
            UUID holdId = UUID.fromString(holdEntry.getKey());
            CargoHold hold = cargoHoldRepository.findById(holdId).orElse(null);
            for (Map.Entry<String, BigDecimal> cargoEntry : holdEntry.getValue().entrySet()) {
                UUID cargoTypeId = UUID.fromString(cargoEntry.getKey());
                BigDecimal weight = cargoEntry.getValue();
                totalWeight = totalWeight.add(weight);

                CargoType cargoType = cargoTypeRepository.findById(cargoTypeId).orElse(null);
                if (cargoType != null) {
                    BigDecimal volume = weight.divide(cargoType.getDensity(), 2, RoundingMode.HALF_UP);
                    totalVolume = totalVolume.add(volume);
                }
            }
        }
        return new BigDecimal[]{totalWeight, totalVolume};
    }

    private CompletableFuture<StabilityResultDTO> calculateVirtualStabilityAsync(
            Ship ship, Map<String, Map<String, BigDecimal>> loadingConfig) {
        return CompletableFuture.supplyAsync(() ->
                calculateVirtualStabilityInternal(ship, loadingConfig), stabilityExecutor);
    }

    private StabilityResultDTO calculateVirtualStabilityInternal(
            Ship ship, Map<String, Map<String, BigDecimal>> loadingConfig) {
        long startTime = System.currentTimeMillis();

        List<CargoLoading> cargoLoadings = convertLoadingConfigToCargoLoadings(loadingConfig);
        List<CargoHold> cargoHolds = cargoHoldRepository.findByShipIdOrderByHoldNumber(ship.getId());

        BigDecimal[] centerOfGravity = calculateCenterOfGravity(ship, cargoLoadings, cargoHolds);
        BigDecimal displacement = calculateDisplacement(ship, cargoLoadings);
        BigDecimal[] centerOfBuoyancy = calculateCenterOfBuoyancy(ship);
        BigDecimal freeSurfaceCorrection = calculateFreeSurfaceCorrection(cargoHolds, displacement);

        BigDecimal gmTransverse = calculateTransverseGM(ship, centerOfGravity[2], centerOfBuoyancy[2], freeSurfaceCorrection);
        BigDecimal gmLongitudinal = calculateLongitudinalGM(ship, centerOfGravity[2], centerOfBuoyancy[2], freeSurfaceCorrection);
        BigDecimal gmUncorrected = gmTransverse.add(freeSurfaceCorrection);

        BigDecimal rightingArm = calculateRightingArm(gmTransverse, BigDecimal.ZERO);
        BigDecimal rightingMoment = calculateRightingMoment(displacement, rightingArm);
        BigDecimal rollPeriod = calculateRollPeriod(ship, gmTransverse, displacement);

        List<Map<String, Object>> curvePoints = generateStabilityCurve(ship, gmTransverse,
                centerOfGravity[2].doubleValue(), displacement);

        String stabilityStatus = determineStabilityStatus(gmTransverse, BigDecimal.ZERO);
        String warningMessage = generateWarningMessage(gmTransverse, BigDecimal.ZERO, null);

        StabilityResultDTO dto = new StabilityResultDTO();
        dto.setShipId(ship.getId());
        dto.setShipName(ship.getName());
        dto.setCalculationTime(LocalDateTime.now());
        dto.setDisplacementActual(displacement);
        dto.setCenterGravityX(centerOfGravity[0]);
        dto.setCenterGravityY(centerOfGravity[1]);
        dto.setCenterGravityZ(centerOfGravity[2]);
        dto.setCenterBuoyancyX(centerOfBuoyancy[0]);
        dto.setCenterBuoyancyY(centerOfBuoyancy[1]);
        dto.setCenterBuoyancyZ(centerOfBuoyancy[2]);
        dto.setMetacentricHeightTransverse(gmTransverse);
        dto.setMetacentricHeightLongitudinal(gmLongitudinal);
        dto.setRightingArm(rightingArm);
        dto.setRightingMoment(rightingMoment);
        dto.setRollPeriod(rollPeriod);
        dto.setGmValue(gmTransverse);
        dto.setFreeSurfaceCorrection(freeSurfaceCorrection);
        dto.setGmUncorrected(gmUncorrected);
        dto.setStabilityStatus(stabilityStatus);
        dto.setWarningMessage(warningMessage);
        dto.setCurvePoints(curvePoints);
        dto.setCreatedAt(LocalDateTime.now());

        long duration = System.currentTimeMillis() - startTime;
        log.debug("[VRLoading] 虚拟稳性计算完成 - 耗时: {}ms, GM: {}m", duration, gmTransverse);

        return dto;
    }

    private BigDecimal[] calculateCenterOfGravity(Ship ship, List<CargoLoading> cargoLoadings,
                                                  List<CargoHold> cargoHolds) {
        BigDecimal totalWeight = ship.getLightshipWeight();
        BigDecimal momentX = ship.getLightshipWeight().multiply(BigDecimal.ZERO);
        BigDecimal momentY = ship.getLightshipWeight().multiply(BigDecimal.ZERO);
        BigDecimal momentZ = ship.getLightshipWeight().multiply(new BigDecimal("0.8"));

        for (CargoHold hold : cargoHolds) {
            if (Boolean.TRUE.equals(hold.getIsTank()) && hold.getTankFullness() != null
                    && hold.getLiquidDensity() != null && hold.getCapacityCubic() != null) {
                double fullness = hold.getTankFullness().doubleValue();
                double liquidWeight = hold.getCapacityCubic().doubleValue()
                        * hold.getLiquidDensity().doubleValue() * fullness;
                if (liquidWeight > 0.01) {
                    BigDecimal lw = BigDecimal.valueOf(liquidWeight);
                    totalWeight = totalWeight.add(lw);
                    momentX = momentX.add(lw.multiply(hold.getCenterGravityX()));
                    momentY = momentY.add(lw.multiply(hold.getCenterGravityY()));
                    momentZ = momentZ.add(lw.multiply(hold.getCenterGravityZ()));
                }
            }
        }

        Map<UUID, CargoHold> holdMap = new HashMap<>();
        for (CargoHold hold : cargoHolds) {
            holdMap.put(hold.getId(), hold);
        }

        for (CargoLoading loading : cargoLoadings) {
            CargoHold hold = holdMap.get(loading.getHoldId());
            if (hold != null) {
                BigDecimal weight = loading.getWeight();
                totalWeight = totalWeight.add(weight);
                momentX = momentX.add(weight.multiply(hold.getCenterGravityX()));
                momentY = momentY.add(weight.multiply(hold.getCenterGravityY()));
                momentZ = momentZ.add(weight.multiply(hold.getCenterGravityZ()));
            }
        }

        BigDecimal cgX = totalWeight.compareTo(BigDecimal.ZERO) > 0
                ? momentX.divide(totalWeight, 3, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal cgY = totalWeight.compareTo(BigDecimal.ZERO) > 0
                ? momentY.divide(totalWeight, 3, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal cgZ = totalWeight.compareTo(BigDecimal.ZERO) > 0
                ? momentZ.divide(totalWeight, 3, RoundingMode.HALF_UP) : BigDecimal.ZERO;

        return new BigDecimal[]{cgX, cgY, cgZ};
    }

    private BigDecimal calculateDisplacement(Ship ship, List<CargoLoading> cargoLoadings) {
        BigDecimal cargoWeight = cargoLoadings.stream()
                .map(CargoLoading::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return ship.getLightshipWeight().add(cargoWeight);
    }

    private BigDecimal[] calculateCenterOfBuoyancy(Ship ship) {
        BigDecimal draft = ship.getDesignDraft();
        BigDecimal cbX = BigDecimal.ZERO;
        BigDecimal cbY = BigDecimal.ZERO;
        double kbCoefficient = shipParamsConfig.getHull().getBuoyancyCenterCoefficient().doubleValue();
        BigDecimal cbZ = draft.multiply(BigDecimal.valueOf(kbCoefficient));
        return new BigDecimal[]{cbX, cbY, cbZ};
    }

    private BigDecimal calculateFreeSurfaceCorrection(List<CargoHold> cargoHolds, BigDecimal displacement) {
        double totalFsc = 0.0;
        double disp = displacement.doubleValue();

        for (CargoHold hold : cargoHolds) {
            if (Boolean.TRUE.equals(hold.getIsTank())
                    && hold.getTankLength() != null
                    && hold.getTankBreadth() != null
                    && hold.getLiquidDensity() != null
                    && hold.getTankFullness() != null) {

                double l = hold.getTankLength().doubleValue();
                double b = hold.getTankBreadth().doubleValue();
                double rho = hold.getLiquidDensity().doubleValue();
                double fullness = hold.getTankFullness().doubleValue();

                if (fullness > 0.01 && fullness < 0.99) {
                    double it = (l * FastMath.pow(b, 3)) / 12.0;
                    double correctionFactor = 1.0 - FastMath.abs(fullness - 0.5) * 0.4;
                    double fsc = (rho * it * correctionFactor) / disp;
                    totalFsc += fsc;
                }
            }
        }
        return BigDecimal.valueOf(totalFsc).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTransverseGM(Ship ship, BigDecimal cgZ, BigDecimal cbZ, BigDecimal fsc) {
        double breadth = ship.getBreadthMolded().doubleValue();
        double length = ship.getLengthOverall().doubleValue();
        double displacement = ship.getDisplacement().doubleValue();
        double seawaterDensity = shipParamsConfig.getHull().getSeawaterDensity().doubleValue();

        double it = (length * FastMath.pow(breadth, 3)) / 12.0;
        double kb = cbZ.doubleValue();
        double bmt = it / displacement * seawaterDensity;
        double km = kb + bmt;
        double gm = km - cgZ.doubleValue() - fsc.doubleValue();

        return BigDecimal.valueOf(gm).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateLongitudinalGM(Ship ship, BigDecimal cgZ, BigDecimal cbZ, BigDecimal fsc) {
        double breadth = ship.getBreadthMolded().doubleValue();
        double length = ship.getLengthOverall().doubleValue();
        double displacement = ship.getDisplacement().doubleValue();
        double seawaterDensity = shipParamsConfig.getHull().getSeawaterDensity().doubleValue();

        double il = (breadth * FastMath.pow(length, 3)) / 12.0;
        double kb = cbZ.doubleValue();
        double bml = il / displacement * seawaterDensity;
        double kml = kb + bml;
        double gml = kml - cgZ.doubleValue() - fsc.doubleValue();

        return BigDecimal.valueOf(gml).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRightingArm(BigDecimal gm, BigDecimal rollAngle) {
        double angleRad = FastMath.toRadians(rollAngle.doubleValue());
        double rightingArm = gm.doubleValue() * FastMath.sin(angleRad);
        return BigDecimal.valueOf(rightingArm).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRightingMoment(BigDecimal displacement, BigDecimal rightingArm) {
        double moment = displacement.doubleValue() * GRAVITY * rightingArm.doubleValue();
        return BigDecimal.valueOf(moment).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateRollPeriod(Ship ship, BigDecimal gm, BigDecimal displacement) {
        if (gm.doubleValue() <= 0) return BigDecimal.ZERO;

        double breadth = ship.getBreadthMolded().doubleValue();
        double kFactor = shipParamsConfig.getHull().getRollRadiusCoefficient().doubleValue();
        double massRadiusOfGyration = breadth * kFactor;
        double period = 2 * Math.PI * massRadiusOfGyration /
                FastMath.sqrt(GRAVITY * gm.doubleValue());

        return BigDecimal.valueOf(period).setScale(3, RoundingMode.HALF_UP);
    }

    private List<Map<String, Object>> generateStabilityCurve(Ship ship, BigDecimal gm,
                                                              double cgZ, BigDecimal displacement) {
        List<Map<String, Object>> curvePoints = new ArrayList<>();
        double step = stabilityConfig.getCurveStep();
        double maxAngle = stabilityConfig.getCurveMaxAngle();

        for (double angle = 0; angle <= maxAngle; angle += step) {
            double angleRad = FastMath.toRadians(angle);
            double rightingArm = gm.doubleValue() * FastMath.sin(angleRad);

            if (angle > 30) {
                double reductionFactor = 1 - (angle - 30) / 60;
                rightingArm *= Math.max(reductionFactor, 0.3);
            }

            double rightingMoment = displacement.doubleValue() * GRAVITY * rightingArm;

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("angle", round(angle, 1));
            point.put("rightingArm", round(rightingArm, 4));
            point.put("rightingMoment", round(rightingMoment, 2));
            curvePoints.add(point);
        }
        return curvePoints;
    }

    private String determineStabilityStatus(BigDecimal gm, BigDecimal rollAngle) {
        BigDecimal minGm = stabilityConfig.getMinGmThreshold();
        BigDecimal maxRoll = stabilityConfig.getMaxRollAngle();

        if (gm.compareTo(new BigDecimal("0.15")) < 0) return "CRITICAL";
        if (gm.compareTo(minGm) < 0) return "WARNING";
        if (rollAngle.abs().compareTo(maxRoll) > 0) return "WARNING";
        return "NORMAL";
    }

    private String generateWarningMessage(BigDecimal gm, BigDecimal rollAngle, BigDecimal bilgeWater) {
        List<String> warnings = new ArrayList<>();
        BigDecimal minGm = stabilityConfig.getMinGmThreshold();
        BigDecimal maxRoll = stabilityConfig.getMaxRollAngle();
        BigDecimal maxBilge = stabilityConfig.getMaxBilgeWater();

        if (gm.compareTo(minGm) < 0) {
            warnings.add(String.format("GM值(%.3fm)低于安全阈值(%.3fm)", gm.doubleValue(), minGm.doubleValue()));
        }
        if (rollAngle != null && rollAngle.abs().compareTo(maxRoll) > 0) {
            warnings.add(String.format("横摇角(%.2f°)超过安全阈值(%.2f°)",
                    rollAngle.abs().doubleValue(), maxRoll.doubleValue()));
        }
        if (bilgeWater != null && bilgeWater.compareTo(maxBilge) > 0) {
            warnings.add(String.format("舱底水位(%.3fm)超过安全阈值(%.3fm)",
                    bilgeWater.doubleValue(), maxBilge.doubleValue()));
        }
        return warnings.isEmpty() ? null : String.join("; ", warnings);
    }

    private double round(double value, int places) {
        BigDecimal bd = BigDecimal.valueOf(value);
        return bd.setScale(places, RoundingMode.HALF_UP).doubleValue();
    }

    private VirtualLoadingResultDTO buildResultDTO(VirtualLoadingSession session, Ship ship,
                                                    StabilityResultDTO stabilityResult, String message) {
        VirtualLoadingResultDTO dto = new VirtualLoadingResultDTO();
        dto.setId(session.getId());
        dto.setShipId(session.getShipId());
        dto.setShipName(ship.getName());
        dto.setSessionName(session.getSessionName());
        dto.setUserId(session.getUserId());
        dto.setIsPublic(session.getIsPublic());
        dto.setLoadingConfig(session.getLoadingConfig());
        dto.setCurrentGm(session.getCurrentGm());
        dto.setStabilityStatus(session.getStabilityStatus());
        dto.setTotalCargoWeight(session.getTotalCargoWeight());
        dto.setTotalCargoVolume(session.getTotalCargoVolume());
        dto.setStepsTaken(session.getStepsTaken());
        dto.setIsActive(session.getIsActive());
        dto.setCreatedAt(session.getCreatedAt());
        dto.setLastActivity(session.getLastActivity());
        dto.setStabilityResult(stabilityResult);
        dto.setMessage(message);

        dto.setLoadingDetails(convertToLoadingDetails(session, ship));
        dto.setHoldInfo(convertToHoldInfo(session, ship));

        return dto;
    }

    private List<CargoLoadingDTO> convertToLoadingDetails(VirtualLoadingSession session, Ship ship) {
        List<CargoLoadingDTO> details = new ArrayList<>();
        Map<String, Map<String, BigDecimal>> config = session.getLoadingConfig();
        if (config == null) {
            return details;
        }

        int order = 1;
        for (Map.Entry<String, Map<String, BigDecimal>> holdEntry : config.entrySet()) {
            UUID holdId = UUID.fromString(holdEntry.getKey());
            CargoHold hold = cargoHoldRepository.findById(holdId).orElse(null);
            for (Map.Entry<String, BigDecimal> cargoEntry : holdEntry.getValue().entrySet()) {
                UUID cargoTypeId = UUID.fromString(cargoEntry.getKey());
                CargoType cargoType = cargoTypeRepository.findById(cargoTypeId).orElse(null);
                BigDecimal weight = cargoEntry.getValue();

                if (weight.compareTo(BigDecimal.ZERO) > 0 && hold != null && cargoType != null) {
                    BigDecimal volume = weight.divide(cargoType.getDensity(), 2, RoundingMode.HALF_UP);

                    CargoLoadingDTO dto = new CargoLoadingDTO();
                    dto.setShipId(session.getShipId());
                    dto.setShipName(ship.getName());
                    dto.setHoldId(holdId);
                    dto.setHoldName(hold.getHoldName());
                    dto.setHoldNumber(hold.getHoldNumber());
                    dto.setCargoTypeId(cargoTypeId);
                    dto.setCargoName(cargoType.getCargoName());
                    dto.setCargoCode(cargoType.getCargoCode());
                    dto.setColorHex(cargoType.getColorHex());
                    dto.setWeight(weight);
                    dto.setVolume(volume);
                    dto.setDensity(cargoType.getDensity());
                    dto.setLoadingOrder(order++);
                    dto.setLoadingTime(LocalDateTime.now());
                    details.add(dto);
                }
            }
        }
        return details;
    }

    private List<CargoHoldDTO> convertToHoldInfo(VirtualLoadingSession session, Ship ship) {
        List<CargoHold> holds = cargoHoldRepository.findByShipIdOrderByHoldNumber(ship.getId());
        Map<String, Map<String, BigDecimal>> config = session.getLoadingConfig();

        return holds.stream().map(hold -> {
            BigDecimal currentWeight = getCurrentHoldWeight(config, hold.getId());
            CargoType firstCargoType = getFirstCargoTypeForHold(config, hold.getId());
            BigDecimal currentVolume = firstCargoType != null && currentWeight.compareTo(BigDecimal.ZERO) > 0
                    ? currentWeight.divide(firstCargoType.getDensity(), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            BigDecimal weightUtilization = hold.getMaxWeight().compareTo(BigDecimal.ZERO) > 0
                    ? currentWeight.divide(hold.getMaxWeight(), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                    : BigDecimal.ZERO;
            BigDecimal volumeUtilization = hold.getCapacityCubic().compareTo(BigDecimal.ZERO) > 0
                    ? currentVolume.divide(hold.getCapacityCubic(), 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                    : BigDecimal.ZERO;

            CargoHoldDTO dto = new CargoHoldDTO();
            dto.setId(hold.getId());
            dto.setShipId(hold.getShipId());
            dto.setHoldNumber(hold.getHoldNumber());
            dto.setHoldName(hold.getHoldName());
            dto.setCapacityCubic(hold.getCapacityCubic());
            dto.setMaxWeight(hold.getMaxWeight());
            dto.setCenterGravityX(hold.getCenterGravityX());
            dto.setCenterGravityY(hold.getCenterGravityY());
            dto.setCenterGravityZ(hold.getCenterGravityZ());
            dto.setIsTank(hold.getIsTank());
            dto.setCurrentWeight(currentWeight);
            dto.setCurrentVolume(currentVolume);
            dto.setWeightUtilizationRate(weightUtilization);
            dto.setVolumeUtilizationRate(volumeUtilization);
            return dto;
        }).collect(Collectors.toList());
    }

    private CargoType getFirstCargoTypeForHold(Map<String, Map<String, BigDecimal>> config, UUID holdId) {
        if (config == null) return null;
        Map<String, BigDecimal> holdConfig = config.get(holdId.toString());
        if (holdConfig == null || holdConfig.isEmpty()) return null;

        String firstCargoId = holdConfig.keySet().iterator().next();
        return cargoTypeRepository.findById(UUID.fromString(firstCargoId)).orElse(null);
    }

    public VirtualLoadingResultDTO getSession(UUID sessionId) {
        log.info("[VRLoading] 获取会话详情 - ID: {}", sessionId);
        VirtualLoadingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));

        Ship ship = shipRepository.findById(session.getShipId())
                .orElseThrow(() -> new IllegalArgumentException("船舶不存在: " + session.getShipId()));

        StabilityResultDTO stabilityResult = calculateVirtualStabilityAsync(ship, session.getLoadingConfig()).join();
        return buildResultDTO(session, ship, stabilityResult, null);
    }

    public List<VirtualLoadingResultDTO> getActiveSessions(UUID shipId) {
        log.info("[VRLoading] 获取船舶活跃会话 - 船舶: {}", shipId);
        List<VirtualLoadingSession> sessions = sessionRepository
                .findByShipIdAndIsActiveOrderByLastActivityDesc(shipId, true);

        return sessions.stream().map(session -> {
            Ship ship = shipRepository.findById(session.getShipId()).orElse(null);
            if (ship == null) return null;
            StabilityResultDTO stabilityResult = calculateVirtualStabilityAsync(ship, session.getLoadingConfig()).join();
            return buildResultDTO(session, ship, stabilityResult, null);
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public Page<VirtualLoadingResultDTO> getPublicSessions(int page, int size) {
        log.info("[VRLoading] 获取公开会话 - 页码: {}, 大小: {}", page, size);
        Page<VirtualLoadingSession> sessionPage = sessionRepository
                .findByIsPublicTrueOrderByCreatedAtDesc(PageRequest.of(page, size));

        return sessionPage.map(session -> {
            Ship ship = shipRepository.findById(session.getShipId()).orElse(null);
            if (ship == null) return null;
            StabilityResultDTO stabilityResult = calculateVirtualStabilityAsync(ship, session.getLoadingConfig()).join();
            return buildResultDTO(session, ship, stabilityResult, null);
        });
    }

    public List<VirtualLoadingResultDTO> getUserSessions(String userId) {
        log.info("[VRLoading] 获取用户会话 - 用户: {}", userId);
        List<VirtualLoadingSession> sessions = sessionRepository
                .findByUserIdOrderByLastActivityDesc(userId);

        return sessions.stream().map(session -> {
            Ship ship = shipRepository.findById(session.getShipId()).orElse(null);
            if (ship == null) return null;
            StabilityResultDTO stabilityResult = calculateVirtualStabilityAsync(ship, session.getLoadingConfig()).join();
            return buildResultDTO(session, ship, stabilityResult, null);
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Transactional
    public void closeSession(UUID sessionId) {
        log.info("[VRLoading] 关闭会话 - ID: {}", sessionId);
        VirtualLoadingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        session.setIsActive(false);
        session.setLastActivity(LocalDateTime.now());
        sessionRepository.save(session);
    }

    @Transactional
    public VirtualLoadingResultDTO cloneSession(UUID sessionId, String newName) {
        log.info("[VRLoading] 克隆会话 - 源ID: {}, 新名称: {}", sessionId, newName);
        VirtualLoadingSession source = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));

        Ship ship = shipRepository.findById(source.getShipId())
                .orElseThrow(() -> new IllegalArgumentException("船舶不存在: " + source.getShipId()));

        VirtualLoadingSession cloned = new VirtualLoadingSession();
        cloned.setShipId(source.getShipId());
        cloned.setSessionName(newName);
        cloned.setUserId(source.getUserId());
        cloned.setIsPublic(source.getIsPublic());
        cloned.setLoadingConfig(new HashMap<>(source.getLoadingConfig()));
        cloned.setCurrentGm(source.getCurrentGm());
        cloned.setStabilityStatus(source.getStabilityStatus());
        cloned.setTotalCargoWeight(source.getTotalCargoWeight());
        cloned.setTotalCargoVolume(source.getTotalCargoVolume());
        cloned.setStepsTaken(0);
        cloned.setIsActive(true);

        StabilityResultDTO stabilityResult = calculateVirtualStabilityAsync(ship, cloned.getLoadingConfig()).join();
        cloned = sessionRepository.save(cloned);

        return buildResultDTO(cloned, ship, stabilityResult, "会话克隆成功");
    }

    @Transactional
    public VirtualLoadingResultDTO saveSession(VirtualLoadingSession session) {
        log.info("[VRLoading] 保存会话 - ID: {}", session.getId());
        if (session.getId() == null) {
            session.setCreatedAt(LocalDateTime.now());
        }
        session.setLastActivity(LocalDateTime.now());
        VirtualLoadingSession saved = sessionRepository.save(session);

        Ship ship = shipRepository.findById(saved.getShipId())
                .orElseThrow(() -> new IllegalArgumentException("船舶不存在: " + saved.getShipId()));
        StabilityResultDTO stabilityResult = calculateVirtualStabilityAsync(ship, saved.getLoadingConfig()).join();

        return buildResultDTO(saved, ship, stabilityResult, "会话保存成功");
    }

    @Transactional
    public void deleteSession(UUID sessionId) {
        log.info("[VRLoading] 删除会话 - ID: {}", sessionId);
        if (!sessionRepository.existsById(sessionId)) {
            throw new IllegalArgumentException("会话不存在: " + sessionId);
        }
        sessionRepository.deleteById(sessionId);
    }

    public Map<UUID, Double> getRecommendations(UUID shipId) {
        log.info("[VRLoading] 获取装载推荐方案 - 船舶: {}", shipId);
        Ship ship = shipRepository.findById(shipId)
                .orElseThrow(() -> new IllegalArgumentException("船舶不存在: " + shipId));
        List<CargoHold> holds = cargoHoldRepository.findByShipIdOrderByHoldNumber(shipId);
        return strategyEngine.recommendLoading(ship, holds);
    }
}
