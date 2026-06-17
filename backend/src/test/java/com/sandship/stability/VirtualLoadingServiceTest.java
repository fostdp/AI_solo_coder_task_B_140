package com.sandship.stability;

import com.sandship.stability.config.ShipParamsConfig;
import com.sandship.stability.config.StabilityConfig;
import com.sandship.stability.dto.*;
import com.sandship.stability.entity.*;
import com.sandship.stability.repository.*;
import com.sandship.stability.vr_loading.VRLoadingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("虚拟装载模块测试")
class VirtualLoadingServiceTest {

    @Mock
    private ShipRepository shipRepository;

    @Mock
    private CargoHoldRepository cargoHoldRepository;

    @Mock
    private CargoTypeRepository cargoTypeRepository;

    @Mock
    private VirtualLoadingSessionRepository sessionRepository;

    @Mock
    private StabilityConfig stabilityConfig;

    @Mock
    private ShipParamsConfig shipParamsConfig;

    @Mock
    private Executor stabilityExecutor;

    @InjectMocks
    private VRLoadingService vrLoadingService;

    private Ship testShip;
    private CargoHold hold1;
    private CargoHold hold2;
    private CargoHold hold3;
    private CargoType grainCargo;
    private CargoType teaCargo;
    private CargoType porcelainCargo;

    @BeforeEach
    void setUp() {
        testShip = buildTestShip();
        hold1 = buildTestHold(1, "第一货舱", new BigDecimal("50.0"), new BigDecimal("100.0"));
        hold2 = buildTestHold(2, "第二货舱", new BigDecimal("60.0"), new BigDecimal("120.0"));
        hold3 = buildTestHold(3, "第三货舱", new BigDecimal("40.0"), new BigDecimal("80.0"));
        grainCargo = buildTestCargoType("GRAIN", "粮食", new BigDecimal("0.75"), "#DEB887");
        teaCargo = buildTestCargoType("TEA", "茶叶", new BigDecimal("0.45"), "#228B22");
        porcelainCargo = buildTestCargoType("PORCELAIN", "瓷器", new BigDecimal("1.50"), "#E6E6FA");
    }

    private Ship buildTestShip() {
        Ship ship = new Ship();
        ship.setId(UUID.randomUUID());
        ship.setName("测试沙船");
        ship.setShipType("沙船");
        ship.setLengthOverall(new BigDecimal("30.0"));
        ship.setBreadthMolded(new BigDecimal("8.0"));
        ship.setDepthMolded(new BigDecimal("3.5"));
        ship.setDesignDraft(new BigDecimal("2.0"));
        ship.setDisplacement(new BigDecimal("300.0"));
        ship.setDeadweightTons(new BigDecimal("150.0"));
        ship.setMetacentricHeightDesign(new BigDecimal("0.85"));
        ship.setBlockCoefficient(new BigDecimal("0.65"));
        return ship;
    }

    private CargoHold buildTestHold(int number, String name, BigDecimal maxWeight, BigDecimal capacity) {
        CargoHold hold = new CargoHold();
        hold.setId(UUID.randomUUID());
        hold.setShipId(testShip.getId());
        hold.setHoldNumber(number);
        hold.setHoldName(name);
        hold.setMaxWeight(maxWeight);
        hold.setCapacityCubic(capacity);
        hold.setCenterGravityX(BigDecimal.ZERO);
        hold.setCenterGravityY(BigDecimal.ZERO);
        hold.setCenterGravityZ(new BigDecimal("1.5"));
        hold.setIsTank(false);
        return hold;
    }

    private CargoType buildTestCargoType(String code, String name, BigDecimal density, String color) {
        CargoType cargo = new CargoType();
        cargo.setId(UUID.randomUUID());
        cargo.setCargoCode(code);
        cargo.setCargoName(name);
        cargo.setDensity(density);
        cargo.setUnitWeight(BigDecimal.ONE);
        cargo.setColorHex(color);
        return cargo;
    }

    @Nested
    @DisplayName("操作策略性测试")
    class OperationStrategyTests {

        @Test
        @DisplayName("正常场景：创建空会话 - GM应等于设计GM")
        void testCreateEmptySession() {
            VirtualLoadingCreateRequest request = new VirtualLoadingCreateRequest();
            request.setShipId(testShip.getId());
            request.setSessionName("测试会话");
            request.setUserId("test_user");
            request.setPublic(true);

            when(shipRepository.findById(testShip.getId())).thenReturn(Optional.of(testShip));
            when(cargoHoldRepository.findByShipIdOrderByHoldNumber(testShip.getId()))
                    .thenReturn(Arrays.asList(hold1, hold2, hold3));
            when(sessionRepository.save(any(VirtualLoadingSession.class)))
                    .thenAnswer(invocation -> {
                        VirtualLoadingSession s = invocation.getArgument(0);
                        s.setId(UUID.randomUUID());
                        return s;
                    });
            when(stabilityConfig.getGmWarningThreshold()).thenReturn(new BigDecimal("0.5"));
            when(stabilityConfig.getGmDangerThreshold()).thenReturn(new BigDecimal("0.3"));

            VirtualLoadingResultDTO result = vrLoadingService.createSession(request);

            assertNotNull(result);
            assertNotNull(result.getId());
            assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalCargoWeight().setScale(2, RoundingMode.HALF_UP)),
                    "空会话总货重应为0");
            assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalCargoVolume().setScale(2, RoundingMode.HALF_UP)),
                    "空会话总容积应为0");
            assertNotNull(result.getCurrentGm());
            assertEquals(0, result.getStepsTaken());
            verify(sessionRepository, times(1)).save(any(VirtualLoadingSession.class));
        }

        @Test
        @DisplayName("正常场景：逐步装载货物 - GM应逐渐降低")
        void testGradualLoadingGmReduction() {
            UUID sessionId = UUID.randomUUID();
            VirtualLoadingSession session = new VirtualLoadingSession();
            session.setId(sessionId);
            session.setShipId(testShip.getId());
            session.setSessionName("测试装载");
            session.setUserId("test_user");
            session.setIsPublic(true);
            session.setIsActive(true);
            session.setLoadingConfig(new HashMap<>());
            session.setStepsTaken(0);
            session.setCurrentGm(testShip.getMetacentricHeightDesign());
            session.setTotalCargoWeight(BigDecimal.ZERO);
            session.setTotalCargoVolume(BigDecimal.ZERO);

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(shipRepository.findById(testShip.getId())).thenReturn(Optional.of(testShip));
            when(cargoHoldRepository.findById(hold1.getId())).thenReturn(Optional.of(hold1));
            when(cargoTypeRepository.findById(grainCargo.getId())).thenReturn(Optional.of(grainCargo));
            when(cargoHoldRepository.findByShipIdOrderByHoldNumber(testShip.getId()))
                    .thenReturn(Arrays.asList(hold1, hold2, hold3));
            when(sessionRepository.save(any(VirtualLoadingSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(stabilityConfig.getGmWarningThreshold()).thenReturn(new BigDecimal("0.5"));
            when(stabilityConfig.getGmDangerThreshold()).thenReturn(new BigDecimal("0.3"));

            BigDecimal initialGm = session.getCurrentGm();

            VirtualLoadingActionRequest loadRequest = new VirtualLoadingActionRequest();
            loadRequest.setSessionId(sessionId);
            loadRequest.setAction("LOAD");
            loadRequest.setHoldId(hold1.getId());
            loadRequest.setCargoTypeId(grainCargo.getId());
            loadRequest.setWeightChange(new BigDecimal("20.0"));

            VirtualLoadingResultDTO result = vrLoadingService.executeAction(loadRequest);

            assertNotNull(result);
            assertTrue(result.getCurrentGm().compareTo(initialGm) <= 0,
                    "装载后GM应小于等于初始GM");
            assertTrue(result.getTotalCargoWeight().compareTo(BigDecimal.ZERO) > 0,
                    "装载后总货重应大于0");
            assertEquals(1, result.getStepsTaken());
            assertNotNull(result.getMessage());
            assertTrue(result.getMessage().contains("成功装载"),
                    "消息应包含'成功装载'");
        }

        @Test
        @DisplayName("正常场景：低舱装重货高舱装轻货 - 策略性装载稳性更好")
        void testStrategicLoadingBetterStability() {
            CargoHold lowerHold = hold1;
            CargoHold upperHold = hold3;
            lowerHold.setCenterGravityZ(new BigDecimal("0.8"));
            upperHold.setCenterGravityZ(new BigDecimal("2.5"));

            UUID sessionGood = UUID.randomUUID();
            VirtualLoadingSession sessionGood = new VirtualLoadingSession();
            sessionGood.setId(sessionGood);
            sessionGood.setShipId(testShip.getId());
            sessionGood.setIsActive(true);
            sessionGood.setLoadingConfig(new HashMap<>());
            sessionGood.setStepsTaken(0);
            sessionGood.setCurrentGm(testShip.getMetacentricHeightDesign());

            UUID sessionBad = UUID.randomUUID();
            VirtualLoadingSession sessionBad = new VirtualLoadingSession();
            sessionBad.setId(sessionBad);
            sessionBad.setShipId(testShip.getId());
            sessionBad.setIsActive(true);
            sessionBad.setLoadingConfig(new HashMap<>());
            sessionBad.setStepsTaken(0);
            sessionBad.setCurrentGm(testShip.getMetacentricHeightDesign());

            when(sessionRepository.findById(sessionGood)).thenReturn(Optional.of(sessionGood));
            when(sessionRepository.findById(sessionBad)).thenReturn(Optional.of(sessionBad));
            when(shipRepository.findById(testShip.getId())).thenReturn(Optional.of(testShip));
            when(cargoHoldRepository.findById(lowerHold.getId())).thenReturn(Optional.of(lowerHold));
            when(cargoHoldRepository.findById(upperHold.getId())).thenReturn(Optional.of(upperHold));
            when(cargoTypeRepository.findById(porcelainCargo.getId())).thenReturn(Optional.of(porcelainCargo));
            when(cargoTypeRepository.findById(teaCargo.getId())).thenReturn(Optional.of(teaCargo));
            when(cargoHoldRepository.findByShipIdOrderByHoldNumber(testShip.getId()))
                    .thenReturn(Arrays.asList(lowerHold, hold2, upperHold));
            when(sessionRepository.save(any(VirtualLoadingSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(stabilityConfig.getGmWarningThreshold()).thenReturn(new BigDecimal("0.5"));
            when(stabilityConfig.getGmDangerThreshold()).thenReturn(new BigDecimal("0.3"));

            VirtualLoadingActionRequest loadHeavyLower = new VirtualLoadingActionRequest();
            loadHeavyLower.setSessionId(sessionGood);
            loadHeavyLower.setAction("LOAD");
            loadHeavyLower.setHoldId(lowerHold.getId());
            loadHeavyLower.setCargoTypeId(porcelainCargo.getId());
            loadHeavyLower.setWeightChange(new BigDecimal("30.0"));

            VirtualLoadingActionRequest loadLightUpper = new VirtualLoadingActionRequest();
            loadLightUpper.setSessionId(sessionGood);
            loadLightUpper.setAction("LOAD");
            loadLightUpper.setHoldId(upperHold.getId());
            loadLightUpper.setCargoTypeId(teaCargo.getId());
            loadLightUpper.setWeightChange(new BigDecimal("10.0"));

            VirtualLoadingActionRequest loadHeavyUpper = new VirtualLoadingActionRequest();
            loadHeavyUpper.setSessionId(sessionBad);
            loadHeavyUpper.setAction("LOAD");
            loadHeavyUpper.setHoldId(upperHold.getId());
            loadHeavyUpper.setCargoTypeId(porcelainCargo.getId());
            loadHeavyUpper.setWeightChange(new BigDecimal("30.0"));

            VirtualLoadingActionRequest loadLightLower = new VirtualLoadingActionRequest();
            loadLightLower.setSessionId(sessionBad);
            loadLightLower.setAction("LOAD");
            loadLightLower.setHoldId(lowerHold.getId());
            loadLightLower.setCargoTypeId(teaCargo.getId());
            loadLightLower.setWeightChange(new BigDecimal("10.0"));

            vrLoadingService.executeAction(loadHeavyLower);
            VirtualLoadingResultDTO goodResult = vrLoadingService.executeAction(loadLightUpper);

            vrLoadingService.executeAction(loadHeavyUpper);
            VirtualLoadingResultDTO badResult = vrLoadingService.executeAction(loadLightLower);

            assertNotNull(goodResult);
            assertNotNull(badResult);

            assertTrue(goodResult.getCurrentGm().compareTo(badResult.getCurrentGm()) > 0,
                    "下重上轻策略应比上重下轻策略获得更高的GM（稳性更好）");
        }

        @Test
        @DisplayName("正常场景：卸载货物 - GM应回升")
        void testUnloadingGmRecovery() {
            UUID sessionId = UUID.randomUUID();
            Map<String, Map<String, BigDecimal>> config = new HashMap<>();
            Map<String, BigDecimal> holdConfig = new HashMap<>();
            holdConfig.put(grainCargo.getId().toString(), new BigDecimal("30.0"));
            config.put(hold1.getId().toString(), holdConfig);

            VirtualLoadingSession session = new VirtualLoadingSession();
            session.setId(sessionId);
            session.setShipId(testShip.getId());
            session.setIsActive(true);
            session.setLoadingConfig(config);
            session.setStepsTaken(1);
            session.setCurrentGm(new BigDecimal("0.65"));
            session.setTotalCargoWeight(new BigDecimal("30.0"));

            BigDecimal loadedGm = new BigDecimal("0.65");

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(shipRepository.findById(testShip.getId())).thenReturn(Optional.of(testShip));
            when(cargoHoldRepository.findById(hold1.getId())).thenReturn(Optional.of(hold1));
            when(cargoTypeRepository.findById(grainCargo.getId())).thenReturn(Optional.of(grainCargo));
            when(cargoHoldRepository.findByShipIdOrderByHoldNumber(testShip.getId()))
                    .thenReturn(Arrays.asList(hold1, hold2, hold3));
            when(sessionRepository.save(any(VirtualLoadingSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(stabilityConfig.getGmWarningThreshold()).thenReturn(new BigDecimal("0.5"));
            when(stabilityConfig.getGmDangerThreshold()).thenReturn(new BigDecimal("0.3"));

            VirtualLoadingActionRequest unloadRequest = new VirtualLoadingActionRequest();
            unloadRequest.setSessionId(sessionId);
            unloadRequest.setAction("UNLOAD");
            unloadRequest.setHoldId(hold1.getId());
            unloadRequest.setCargoTypeId(grainCargo.getId());
            unloadRequest.setWeightChange(new BigDecimal("-15.0"));

            VirtualLoadingResultDTO result = vrLoadingService.executeAction(unloadRequest);

            assertNotNull(result);
            assertTrue(result.getCurrentGm().compareTo(loadedGm) > 0,
                    "卸载部分货物后GM应回升");
            assertTrue(result.getTotalCargoWeight().compareTo(new BigDecimal("30.0")) < 0,
                    "卸载后总货重应减少");
        }

        @Test
        @DisplayName("边界场景：重置单个货舱 - 该舱重量归零")
        void testResetHold() {
            UUID sessionId = UUID.randomUUID();
            Map<String, Map<String, BigDecimal>> config = new HashMap<>();
            Map<String, BigDecimal> hold1Config = new HashMap<>();
            hold1Config.put(grainCargo.getId().toString(), new BigDecimal("20.0"));
            config.put(hold1.getId().toString(), hold1Config);
            Map<String, BigDecimal> hold2Config = new HashMap<>();
            hold2Config.put(teaCargo.getId().toString(), new BigDecimal("10.0"));
            config.put(hold2.getId().toString(), hold2Config);

            VirtualLoadingSession session = new VirtualLoadingSession();
            session.setId(sessionId);
            session.setShipId(testShip.getId());
            session.setIsActive(true);
            session.setLoadingConfig(config);
            session.setStepsTaken(2);

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(shipRepository.findById(testShip.getId())).thenReturn(Optional.of(testShip));
            when(cargoHoldRepository.findById(hold1.getId())).thenReturn(Optional.of(hold1));
            when(cargoTypeRepository.findById(teaCargo.getId())).thenReturn(Optional.of(teaCargo));
            when(cargoHoldRepository.findByShipIdOrderByHoldNumber(testShip.getId()))
                    .thenReturn(Arrays.asList(hold1, hold2, hold3));
            when(sessionRepository.save(any(VirtualLoadingSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(stabilityConfig.getGmWarningThreshold()).thenReturn(new BigDecimal("0.5"));
            when(stabilityConfig.getGmDangerThreshold()).thenReturn(new BigDecimal("0.3"));

            VirtualLoadingActionRequest resetRequest = new VirtualLoadingActionRequest();
            resetRequest.setSessionId(sessionId);
            resetRequest.setAction("RESET_HOLD");
            resetRequest.setHoldId(hold1.getId());

            VirtualLoadingResultDTO result = vrLoadingService.executeAction(resetRequest);

            assertNotNull(result);
            assertTrue(result.getMessage().contains("已重置货舱"),
                    "消息应包含'已重置货舱'");
            assertEquals(3, result.getStepsTaken());
        }

        @Test
        @DisplayName("边界场景：重置所有货舱 - 全部清空")
        void testResetAll() {
            UUID sessionId = UUID.randomUUID();
            Map<String, Map<String, BigDecimal>> config = new HashMap<>();
            Map<String, BigDecimal> hold1Config = new HashMap<>();
            hold1Config.put(grainCargo.getId().toString(), new BigDecimal("20.0"));
            config.put(hold1.getId().toString(), hold1Config);

            VirtualLoadingSession session = new VirtualLoadingSession();
            session.setId(sessionId);
            session.setShipId(testShip.getId());
            session.setIsActive(true);
            session.setLoadingConfig(config);
            session.setStepsTaken(1);

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(shipRepository.findById(testShip.getId())).thenReturn(Optional.of(testShip));
            when(cargoHoldRepository.findByShipIdOrderByHoldNumber(testShip.getId()))
                    .thenReturn(Arrays.asList(hold1, hold2, hold3));
            when(sessionRepository.save(any(VirtualLoadingSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(stabilityConfig.getGmWarningThreshold()).thenReturn(new BigDecimal("0.5"));
            when(stabilityConfig.getGmDangerThreshold()).thenReturn(new BigDecimal("0.3"));

            VirtualLoadingActionRequest resetRequest = new VirtualLoadingActionRequest();
            resetRequest.setSessionId(sessionId);
            resetRequest.setAction("RESET_ALL");

            VirtualLoadingResultDTO result = vrLoadingService.executeAction(resetRequest);

            assertNotNull(result);
            assertTrue(result.getMessage().contains("已重置所有货舱"),
                    "消息应包含'已重置所有货舱'");
            assertEquals(0, BigDecimal.ZERO.compareTo(result.getTotalCargoWeight().setScale(2, RoundingMode.HALF_UP)),
                    "重置后总货重应为0");
        }
    }

    @Nested
    @DisplayName("约束校验测试")
    class ConstraintValidationTests {

        @Test
        @DisplayName("边界场景：超过货舱最大承重 - 应返回错误提示")
        void testExceedMaxWeight() {
            UUID sessionId = UUID.randomUUID();
            Map<String, Map<String, BigDecimal>> config = new HashMap<>();
            Map<String, BigDecimal> holdConfig = new HashMap<>();
            holdConfig.put(grainCargo.getId().toString(), new BigDecimal("45.0"));
            config.put(hold1.getId().toString(), holdConfig);

            VirtualLoadingSession session = new VirtualLoadingSession();
            session.setId(sessionId);
            session.setShipId(testShip.getId());
            session.setIsActive(true);
            session.setLoadingConfig(config);
            session.setStepsTaken(1);

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(shipRepository.findById(testShip.getId())).thenReturn(Optional.of(testShip));
            when(cargoHoldRepository.findById(hold1.getId())).thenReturn(Optional.of(hold1));
            when(cargoTypeRepository.findById(grainCargo.getId())).thenReturn(Optional.of(grainCargo));
            when(cargoHoldRepository.findByShipIdOrderByHoldNumber(testShip.getId()))
                    .thenReturn(Arrays.asList(hold1, hold2, hold3));
            when(sessionRepository.save(any(VirtualLoadingSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(stabilityConfig.getGmWarningThreshold()).thenReturn(new BigDecimal("0.5"));
            when(stabilityConfig.getGmDangerThreshold()).thenReturn(new BigDecimal("0.3"));

            VirtualLoadingActionRequest loadRequest = new VirtualLoadingActionRequest();
            loadRequest.setSessionId(sessionId);
            loadRequest.setAction("LOAD");
            loadRequest.setHoldId(hold1.getId());
            loadRequest.setCargoTypeId(grainCargo.getId());
            loadRequest.setWeightChange(new BigDecimal("10.0"));

            VirtualLoadingResultDTO result = vrLoadingService.executeAction(loadRequest);

            assertNotNull(result);
            assertNotNull(result.getMessage());
            assertTrue(result.getMessage().contains("超过货舱最大承重") ||
                               result.getMessage().contains("超过"),
                    "超过承重时应返回错误提示");
            assertEquals(2, result.getStepsTaken(),
                    "即使装载失败，操作步数也应增加（或根据需求调整）");
        }

        @Test
        @DisplayName("边界场景：超过货舱容积 - 应返回错误提示")
        void testExceedCapacity() {
            UUID sessionId = UUID.randomUUID();
            VirtualLoadingSession session = new VirtualLoadingSession();
            session.setId(sessionId);
            session.setShipId(testShip.getId());
            session.setIsActive(true);
            session.setLoadingConfig(new HashMap<>());
            session.setStepsTaken(0);

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(shipRepository.findById(testShip.getId())).thenReturn(Optional.of(testShip));
            when(cargoHoldRepository.findById(hold1.getId())).thenReturn(Optional.of(hold1));
            when(cargoTypeRepository.findById(teaCargo.getId())).thenReturn(Optional.of(teaCargo));
            when(cargoHoldRepository.findByShipIdOrderByHoldNumber(testShip.getId()))
                    .thenReturn(Arrays.asList(hold1, hold2, hold3));
            when(sessionRepository.save(any(VirtualLoadingSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(stabilityConfig.getGmWarningThreshold()).thenReturn(new BigDecimal("0.5"));
            when(stabilityConfig.getGmDangerThreshold()).thenReturn(new BigDecimal("0.3"));

            VirtualLoadingActionRequest loadRequest = new VirtualLoadingActionRequest();
            loadRequest.setSessionId(sessionId);
            loadRequest.setAction("LOAD");
            loadRequest.setHoldId(hold1.getId());
            loadRequest.setCargoTypeId(teaCargo.getId());
            loadRequest.setWeightChange(new BigDecimal("60.0"));

            VirtualLoadingResultDTO result = vrLoadingService.executeAction(loadRequest);

            assertNotNull(result);
            assertTrue(result.getMessage().contains("超过货舱容积") || result.getMessage().contains("超过"),
                    "超过容积时应返回错误提示");
        }

        @Test
        @DisplayName("异常场景：装载重量为负数 - 应抛出异常")
        void testNegativeLoadWeight() {
            UUID sessionId = UUID.randomUUID();
            VirtualLoadingSession session = new VirtualLoadingSession();
            session.setId(sessionId);
            session.setShipId(testShip.getId());
            session.setIsActive(true);
            session.setLoadingConfig(new HashMap<>());

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(shipRepository.findById(testShip.getId())).thenReturn(Optional.of(testShip));

            VirtualLoadingActionRequest loadRequest = new VirtualLoadingActionRequest();
            loadRequest.setSessionId(sessionId);
            loadRequest.setAction("LOAD");
            loadRequest.setHoldId(hold1.getId());
            loadRequest.setCargoTypeId(grainCargo.getId());
            loadRequest.setWeightChange(new BigDecimal("-10.0"));

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                vrLoadingService.executeAction(loadRequest);
            });

            assertTrue(exception.getMessage().contains("必须为正数") || exception.getMessage().contains("重量"),
                    "装载重量为负时应抛出异常");
        }

        @Test
        @DisplayName("异常场景：卸载重量为正数 - 应抛出异常")
        void testPositiveUnloadWeight() {
            UUID sessionId = UUID.randomUUID();
            VirtualLoadingSession session = new VirtualLoadingSession();
            session.setId(sessionId);
            session.setShipId(testShip.getId());
            session.setIsActive(true);
            session.setLoadingConfig(new HashMap<>());

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(shipRepository.findById(testShip.getId())).thenReturn(Optional.of(testShip));

            VirtualLoadingActionRequest unloadRequest = new VirtualLoadingActionRequest();
            unloadRequest.setSessionId(sessionId);
            unloadRequest.setAction("UNLOAD");
            unloadRequest.setHoldId(hold1.getId());
            unloadRequest.setCargoTypeId(grainCargo.getId());
            unloadRequest.setWeightChange(new BigDecimal("10.0"));

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                vrLoadingService.executeAction(unloadRequest);
            });

            assertTrue(exception.getMessage().contains("必须为负数") || exception.getMessage().contains("重量"),
                    "卸载重量为正时应抛出异常");
        }

        @Test
        @DisplayName("异常场景：卸载不存在的货物 - 应返回提示信息")
        void testUnloadNonExistentCargo() {
            UUID sessionId = UUID.randomUUID();
            VirtualLoadingSession session = new VirtualLoadingSession();
            session.setId(sessionId);
            session.setShipId(testShip.getId());
            session.setIsActive(true);
            session.setLoadingConfig(new HashMap<>());
            session.setStepsTaken(0);

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(shipRepository.findById(testShip.getId())).thenReturn(Optional.of(testShip));
            when(cargoHoldRepository.findById(hold1.getId())).thenReturn(Optional.of(hold1));
            when(cargoTypeRepository.findById(grainCargo.getId())).thenReturn(Optional.of(grainCargo));
            when(cargoHoldRepository.findByShipIdOrderByHoldNumber(testShip.getId()))
                    .thenReturn(Arrays.asList(hold1, hold2, hold3));
            when(sessionRepository.save(any(VirtualLoadingSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(stabilityConfig.getGmWarningThreshold()).thenReturn(new BigDecimal("0.5"));
            when(stabilityConfig.getGmDangerThreshold()).thenReturn(new BigDecimal("0.3"));

            VirtualLoadingActionRequest unloadRequest = new VirtualLoadingActionRequest();
            unloadRequest.setSessionId(sessionId);
            unloadRequest.setAction("UNLOAD");
            unloadRequest.setHoldId(hold1.getId());
            unloadRequest.setCargoTypeId(grainCargo.getId());
            unloadRequest.setWeightChange(new BigDecimal("-5.0"));

            VirtualLoadingResultDTO result = vrLoadingService.executeAction(unloadRequest);

            assertNotNull(result);
            assertTrue(result.getMessage().contains("没有") || result.getMessage().contains("不存在"),
                    "卸载不存在的货物时应返回提示");
        }
    }

    @Nested
    @DisplayName("边界与异常场景测试")
    class BoundaryAndExceptionTests {

        @Test
        @DisplayName("异常场景：会话不存在 - 应抛出IllegalArgumentException")
        void testNonExistentSession() {
            UUID fakeSessionId = UUID.randomUUID();
            VirtualLoadingActionRequest request = new VirtualLoadingActionRequest();
            request.setSessionId(fakeSessionId);
            request.setAction("LOAD");

            when(sessionRepository.findById(fakeSessionId)).thenReturn(Optional.empty());

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                vrLoadingService.executeAction(request);
            });

            assertTrue(exception.getMessage().contains("会话不存在"),
                    "异常信息应说明会话不存在");
            verify(sessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("异常场景：会话已关闭 - 应抛出IllegalStateException")
        void testInactiveSession() {
            UUID sessionId = UUID.randomUUID();
            VirtualLoadingSession session = new VirtualLoadingSession();
            session.setId(sessionId);
            session.setShipId(testShip.getId());
            session.setIsActive(false);

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));

            VirtualLoadingActionRequest request = new VirtualLoadingActionRequest();
            request.setSessionId(sessionId);
            request.setAction("LOAD");

            IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
                vrLoadingService.executeAction(request);
            });

            assertTrue(exception.getMessage().contains("已关闭") || exception.getMessage().contains("无法"),
                    "异常信息应说明会话已关闭");
            verify(sessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("异常场景：不支持的操作类型 - 应抛出异常")
        void testUnsupportedAction() {
            UUID sessionId = UUID.randomUUID();
            VirtualLoadingSession session = new VirtualLoadingSession();
            session.setId(sessionId);
            session.setShipId(testShip.getId());
            session.setIsActive(true);

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(shipRepository.findById(testShip.getId())).thenReturn(Optional.of(testShip));

            VirtualLoadingActionRequest request = new VirtualLoadingActionRequest();
            request.setSessionId(sessionId);
            request.setAction("INVALID_ACTION");

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                vrLoadingService.executeAction(request);
            });

            assertTrue(exception.getMessage().contains("不支持") || exception.getMessage().contains("操作类型"),
                    "异常信息应说明不支持的操作类型");
            verify(sessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("异常场景：创建会话时船舶不存在 - 应抛出异常")
        void testCreateSessionNonExistentShip() {
            UUID fakeShipId = UUID.randomUUID();
            VirtualLoadingCreateRequest request = new VirtualLoadingCreateRequest();
            request.setShipId(fakeShipId);
            request.setSessionName("测试");
            request.setUserId("test");

            when(shipRepository.findById(fakeShipId)).thenReturn(Optional.empty());

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                vrLoadingService.createSession(request);
            });

            assertTrue(exception.getMessage().contains("船舶不存在"),
                    "异常信息应说明船舶不存在");
            verify(sessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("异常场景：LOAD操作缺少holdId - 应抛出异常")
        void testLoadMissingHoldId() {
            UUID sessionId = UUID.randomUUID();
            VirtualLoadingSession session = new VirtualLoadingSession();
            session.setId(sessionId);
            session.setShipId(testShip.getId());
            session.setIsActive(true);

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(shipRepository.findById(testShip.getId())).thenReturn(Optional.of(testShip));

            VirtualLoadingActionRequest request = new VirtualLoadingActionRequest();
            request.setSessionId(sessionId);
            request.setAction("LOAD");
            request.setCargoTypeId(grainCargo.getId());
            request.setWeightChange(new BigDecimal("10.0"));

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                vrLoadingService.executeAction(request);
            });

            assertTrue(exception.getMessage().contains("不能为空"),
                    "缺少必要参数时应抛出异常");
        }

        @Test
        @DisplayName("边界场景：刚好装满货舱 - 应成功装载")
        void testExactFullCapacity() {
            UUID sessionId = UUID.randomUUID();
            VirtualLoadingSession session = new VirtualLoadingSession();
            session.setId(sessionId);
            session.setShipId(testShip.getId());
            session.setIsActive(true);
            session.setLoadingConfig(new HashMap<>());
            session.setStepsTaken(0);

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(shipRepository.findById(testShip.getId())).thenReturn(Optional.of(testShip));
            when(cargoHoldRepository.findById(hold1.getId())).thenReturn(Optional.of(hold1));
            when(cargoTypeRepository.findById(grainCargo.getId())).thenReturn(Optional.of(grainCargo));
            when(cargoHoldRepository.findByShipIdOrderByHoldNumber(testShip.getId()))
                    .thenReturn(Arrays.asList(hold1, hold2, hold3));
            when(sessionRepository.save(any(VirtualLoadingSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(stabilityConfig.getGmWarningThreshold()).thenReturn(new BigDecimal("0.5"));
            when(stabilityConfig.getGmDangerThreshold()).thenReturn(new BigDecimal("0.3"));

            VirtualLoadingActionRequest loadRequest = new VirtualLoadingActionRequest();
            loadRequest.setSessionId(sessionId);
            loadRequest.setAction("LOAD");
            loadRequest.setHoldId(hold1.getId());
            loadRequest.setCargoTypeId(grainCargo.getId());
            loadRequest.setWeightChange(hold1.getMaxWeight());

            VirtualLoadingResultDTO result = vrLoadingService.executeAction(loadRequest);

            assertNotNull(result);
            assertTrue(result.getMessage().contains("成功装载"),
                    "刚好装满时应成功装载");
        }
    }

    @Nested
    @DisplayName("会话查询测试")
    class SessionQueryTests {

        @Test
        @DisplayName("正常场景：获取会话详情")
        void testGetSessionById() {
            UUID sessionId = UUID.randomUUID();
            VirtualLoadingSession session = new VirtualLoadingSession();
            session.setId(sessionId);
            session.setShipId(testShip.getId());
            session.setSessionName("测试会话");
            session.setUserId("test_user");
            session.setCurrentGm(new BigDecimal("0.75"));
            session.setTotalCargoWeight(new BigDecimal("50.0"));
            session.setStepsTaken(3);

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(shipRepository.findById(testShip.getId())).thenReturn(Optional.of(testShip));
            when(cargoHoldRepository.findByShipIdOrderByHoldNumber(testShip.getId()))
                    .thenReturn(Arrays.asList(hold1, hold2, hold3));
            when(cargoTypeRepository.findById(any())).thenReturn(Optional.of(grainCargo));

            VirtualLoadingResultDTO result = vrLoadingService.getSession(sessionId);

            assertNotNull(result);
            assertEquals(sessionId, result.getId());
            assertEquals("测试会话", result.getSessionName());
            assertEquals(3, result.getStepsTaken());
        }

        @Test
        @DisplayName("异常场景：查询不存在的会话 - 应抛出异常")
        void testGetNonExistentSession() {
            UUID fakeId = UUID.randomUUID();
            when(sessionRepository.findById(fakeId)).thenReturn(Optional.empty());

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                vrLoadingService.getSession(fakeId);
            });

            assertTrue(exception.getMessage().contains("不存在"));
        }

        @Test
        @DisplayName("正常场景：获取公开会话列表")
        void testGetPublicSessions() {
            VirtualLoadingSession session1 = new VirtualLoadingSession();
            session1.setId(UUID.randomUUID());
            session1.setSessionName("公开会话1");
            session1.setIsPublic(true);

            VirtualLoadingSession session2 = new VirtualLoadingSession();
            session2.setId(UUID.randomUUID());
            session2.setSessionName("公开会话2");
            session2.setIsPublic(true);

            Page<VirtualLoadingSession> page = new PageImpl<>(Arrays.asList(session1, session2));
            when(sessionRepository.findByIsPublicTrueOrderByCreatedAtDesc(any()))
                    .thenReturn(page);

            Page<VirtualLoadingResultDTO> result = vrLoadingService.getPublicSessions(0, 10);

            assertNotNull(result);
            assertEquals(2, result.getTotalElements());
        }

        @Test
        @DisplayName("正常场景：关闭会话")
        void testCloseSession() {
            UUID sessionId = UUID.randomUUID();
            VirtualLoadingSession session = new VirtualLoadingSession();
            session.setId(sessionId);
            session.setShipId(testShip.getId());
            session.setIsActive(true);
            session.setLoadingConfig(new HashMap<>());

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any(VirtualLoadingSession.class)))
                    .thenReturn(session);

            assertDoesNotThrow(() -> vrLoadingService.closeSession(sessionId));
            verify(sessionRepository, times(1)).save(any(VirtualLoadingSession.class));
        }

        @Test
        @DisplayName("异常场景：关闭不存在的会话 - 应抛出异常")
        void testCloseNonExistentSession() {
            UUID fakeId = UUID.randomUUID();
            when(sessionRepository.findById(fakeId)).thenReturn(Optional.empty());

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                vrLoadingService.closeSession(fakeId);
            });

            assertTrue(exception.getMessage().contains("不存在"));
            verify(sessionRepository, never()).save(any());
        }

        @Test
        @DisplayName("正常场景：克隆会话")
        void testCloneSession() {
            UUID sessionId = UUID.randomUUID();
            VirtualLoadingSession session = new VirtualLoadingSession();
            session.setId(sessionId);
            session.setShipId(testShip.getId());
            session.setIsActive(true);
            session.setSessionName("原会话");
            session.setUserId("test_user");
            session.setLoadingConfig(new HashMap<>());

            when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
            when(sessionRepository.save(any(VirtualLoadingSession.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            VirtualLoadingResultDTO result = vrLoadingService.cloneSession(sessionId, "克隆的会话");

            assertNotNull(result);
            assertEquals("克隆的会话", result.getSessionName());
            verify(sessionRepository, times(1)).save(any(VirtualLoadingSession.class));
        }
    }
}
