package com.sandship.stability.util;

import com.sandship.stability.entity.*;
import com.sandship.stability.dto.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

public class TestDataBuilder {

    public static Ship buildShip(String name, String category, String family, String variant,
                                  BigDecimal length, BigDecimal breadth, BigDecimal depth,
                                  BigDecimal draft, BigDecimal displacement, BigDecimal deadweight,
                                  BigDecimal gmDesign, BigDecimal bowHeight) {
        Ship ship = new Ship();
        ship.setId(UUID.randomUUID());
        ship.setName(name);
        ship.setShipType(family);
        ship.setShipCategory(category);
        ship.setShipFamily(family);
        ship.setShipVariant(variant);
        ship.setLengthOverall(length);
        ship.setBreadthMolded(breadth);
        ship.setDepthMolded(depth);
        ship.setDesignDraft(draft);
        ship.setDisplacement(displacement);
        ship.setDeadweightTons(deadweight);
        ship.setMetacentricHeightDesign(gmDesign);
        ship.setBowHeight(bowHeight);
        ship.setBlockCoefficient(new BigDecimal("0.65"));
        ship.setRollRadiusCoefficient(new BigDecimal("0.40"));
        ship.setWatertightBulkheads(8);
        ship.setHistoricalPeriod("明代");
        ship.setCreatedAt(LocalDateTime.now());
        return ship;
    }

    public static Ship buildSandShip() {
        return buildShip("测试沙船", "ANCIENT", "沙船", "标准沙船",
                new BigDecimal("30.0"), new BigDecimal("8.0"), new BigDecimal("3.5"),
                new BigDecimal("2.0"), new BigDecimal("300.0"), new BigDecimal("150.0"),
                new BigDecimal("0.85"), new BigDecimal("2.5"));
    }

    public static Ship buildFuChuan() {
        return buildShip("测试福船", "ANCIENT", "福船", "中型福船",
                new BigDecimal("48.0"), new BigDecimal("11.0"), new BigDecimal("5.0"),
                new BigDecimal("3.2"), new BigDecimal("800.0"), new BigDecimal("450.0"),
                new BigDecimal("1.20"), new BigDecimal("5.2"));
    }

    public static Ship buildGuangChuan() {
        return buildShip("测试广船", "ANCIENT", "广船", "中型广船",
                new BigDecimal("42.0"), new BigDecimal("10.0"), new BigDecimal("4.5"),
                new BigDecimal("2.8"), new BigDecimal("650.0"), new BigDecimal("350.0"),
                new BigDecimal("1.05"), new BigDecimal("4.8"));
    }

    public static Ship buildModernBulkCarrier() {
        return buildShip("现代散货船", "MODERN", "散货船", "Handysize 35000DWT",
                new BigDecimal("180.0"), new BigDecimal("30.0"), new BigDecimal("15.0"),
                new BigDecimal("10.0"), new BigDecimal("35000.0"), new BigDecimal("35000.0"),
                new BigDecimal("2.50"), new BigDecimal("12.0"));
    }

    public static SensorData buildSensorData(UUID shipId, BigDecimal draft, BigDecimal waveHeight) {
        SensorData data = new SensorData();
        data.setId(UUID.randomUUID());
        data.setShipId(shipId);
        data.setTimestamp(LocalDateTime.now());
        data.setRollAngle(BigDecimal.ZERO);
        data.setPitchAngle(BigDecimal.ZERO);
        data.setHeelAngle(BigDecimal.ZERO);
        data.setDraftMean(draft);
        data.setDraftForward(draft.multiply(new BigDecimal("1.02")));
        data.setDraftAft(draft.multiply(new BigDecimal("0.98")));
        data.setWaveHeight(waveHeight);
        data.setBilgeWaterLevel(BigDecimal.ZERO);
        return data;
    }

    public static ShipComparisonRequest buildShipComparisonRequest(List<UUID> shipIds) {
        ShipComparisonRequest request = new ShipComparisonRequest();
        request.setComparisonName("单元测试对比");
        request.setShipIds(shipIds);
        request.setComparisonCriteria(Arrays.asList("GM", "GZ_MAX", "RANGE", "GZ_AREA", "ROLL_PERIOD"));
        request.setLoadingCondition("FULL_LOAD");
        request.setReferenceWaveHeight(new BigDecimal("3.0"));
        request.setCreatedBy("test_user");
        return request;
    }

    public static StormSimulationRequest buildStormSimulationRequest(String severity, BigDecimal waveHeight) {
        StormSimulationRequest request = new StormSimulationRequest();
        request.setStormSeverity(severity);
        request.setWaveHeight(waveHeight);
        request.setWindSpeed(waveHeight.multiply(new BigDecimal("3.0")).add(new BigDecimal("10")));
        request.setWavePeriod(new BigDecimal("10.0"));
        request.setMonteCarloIterations(1000);
        request.setLoadingCondition("FULL_LOAD");
        return request;
    }

    public static VirtualLoadingCreateRequest buildVirtualLoadingRequest(UUID shipId) {
        VirtualLoadingCreateRequest request = new VirtualLoadingCreateRequest();
        request.setShipId(shipId);
        request.setSessionName("测试装载会话");
        request.setUserId("test_user");
        request.setIsPublic(true);
        return request;
    }

    public static VirtualLoadingActionRequest buildLoadingAction(UUID holdId, String cargoCode,
                                                                   BigDecimal weight, BigDecimal volume) {
        VirtualLoadingActionRequest request = new VirtualLoadingActionRequest();
        request.setActionType("LOAD");
        request.setHoldId(holdId);
        request.setCargoCode(cargoCode);
        request.setWeight(weight);
        request.setVolume(volume);
        return request;
    }

    public static CargoHold buildCargoHold(UUID shipId, String code, int position,
                                            BigDecimal maxWeight, BigDecimal maxVolume) {
        CargoHold hold = new CargoHold();
        hold.setId(UUID.randomUUID());
        hold.setShipId(shipId);
        hold.setHoldCode(code);
        hold.setHoldName("货舱" + code);
        hold.setPosition(position);
        hold.setMaxWeight(maxWeight);
        hold.setMaxVolume(maxVolume);
        hold.setCurrentWeight(BigDecimal.ZERO);
        hold.setCurrentVolume(BigDecimal.ZERO);
        hold.setLongitudinalCenter(position == 1 ? new BigDecimal("-8.0") :
                position == 2 ? new BigDecimal("-2.5") :
                position == 3 ? new BigDecimal("2.5") : new BigDecimal("8.0"));
        hold.setVerticalCenter(new BigDecimal("1.2"));
        hold.setCreatedAt(LocalDateTime.now());
        return hold;
    }

    public static CargoType buildCargoType(String code, String name, BigDecimal density, String color) {
        CargoType type = new CargoType();
        type.setId(UUID.randomUUID());
        type.setCargoCode(code);
        type.setCargoName(name);
        type.setDensity(density);
        type.setColorCode(color);
        type.setDescription("测试货物：" + name);
        type.setCreatedAt(LocalDateTime.now());
        return type;
    }

    public static List<BigDecimal> generateRollTimeSeries(int points, double amplitude, double period) {
        List<BigDecimal> series = new ArrayList<>();
        for (int i = 0; i < points; i++) {
            double t = i * 0.5;
            double angle = amplitude * Math.sin(2 * Math.PI * t / period);
            series.add(BigDecimal.valueOf(angle).setScale(3, RoundingMode.HALF_UP));
        }
        return series;
    }
}
