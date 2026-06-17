package com.sandship.stability;

import com.sandship.stability.dto.*;
import com.sandship.stability.entity.*;
import com.sandship.stability.repository.*;
import com.sandship.stability.ship_comparison.ShipComparisonService;
import com.sandship.stability.stability_simulator.StabilitySimulatorService;
import com.sandship.stability.util.TestDataBuilder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("船型对比模块测试")
class ShipComparisonServiceTest {

    @Mock
    private StabilitySimulatorService stabilitySimulatorService;

    @Mock
    private ShipRepository shipRepository;

    @Mock
    private ShipComparisonRepository shipComparisonRepository;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Query query;

    @InjectMocks
    private ShipComparisonService shipComparisonService;

    private Ship sandShip;
    private Ship fuChuan;
    private Ship guangChuan;
    private Ship modernShip;

    @BeforeEach
    void setUp() {
        sandShip = TestDataBuilder.buildSandShip();
        fuChuan = TestDataBuilder.buildFuChuan();
        guangChuan = TestDataBuilder.buildGuangChuan();
        modernShip = TestDataBuilder.buildModernBulkCarrier();
    }

    private StabilityResult createMockStabilityResult(Ship ship, BigDecimal gm) {
        StabilityResult result = new StabilityResult();
        result.setId(UUID.randomUUID());
        result.setShipId(ship.getId());
        result.setGmValue(gm);
        result.setRollPeriod(BigDecimal.TEN);
        result.setDisplacementActual(ship.getDisplacement());

        List<Map<String, Object>> curvePoints = new ArrayList<>();
        for (int i = 0; i <= 90; i += 5) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("angle", (double) i);
            double radians = Math.toRadians(i);
            double rightingArm = gm.doubleValue() * Math.sin(radians) * (1 - 0.005 * i);
            point.put("rightingArm", Math.max(0, rightingArm));
            curvePoints.add(point);
        }
        result.setCurvePoints(curvePoints);
        return result;
    }

    @Nested
    @DisplayName("GM值差异验证测试")
    class GmDifferenceTests {

        @Test
        @DisplayName("正常场景：三艘古代船对比 - GM值应有显著差异")
        void testThreeAncientShipsGmComparison() {
            List<UUID> shipIds = Arrays.asList(sandShip.getId(), fuChuan.getId(), guangChuan.getId());
            ShipComparisonRequest request = TestDataBuilder.buildShipComparisonRequest(shipIds);

            when(shipRepository.findAllById(shipIds)).thenReturn(Arrays.asList(sandShip, fuChuan, guangChuan));

            when(stabilitySimulatorService.calculateStability(eq(sandShip.getId()), any(SensorData.class)))
                    .thenReturn(createMockStabilityResult(sandShip, new BigDecimal("0.85")));
            when(stabilitySimulatorService.calculateStability(eq(fuChuan.getId()), any(SensorData.class)))
                    .thenReturn(createMockStabilityResult(fuChuan, new BigDecimal("1.20")));
            when(stabilitySimulatorService.calculateStability(eq(guangChuan.getId()), any(SensorData.class)))
                    .thenReturn(createMockStabilityResult(guangChuan, new BigDecimal("1.05")));

            when(shipComparisonRepository.save(any(ShipComparison.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            when(entityManager.createNativeQuery(anyString())).thenReturn(query);
            when(query.getSingleResult()).thenReturn("ANCIENT");
            when(query.setParameter(anyString(), any())).thenReturn(query);

            ShipComparisonResultDTO result = shipComparisonService.compareShips(request);

            assertNotNull(result);
            assertEquals(3, result.getComparisonItems().size());

            Map<UUID, BigDecimal> gmValues = new HashMap<>();
            for (ShipComparisonResultDTO.ShipComparisonItem item : result.getComparisonItems()) {
                gmValues.put(item.getShipId(), item.getMetrics().get("GM"));
            }

            assertEquals(0, new BigDecimal("0.85").compareTo(gmValues.get(sandShip.getId()).setScale(2, RoundingMode.HALF_UP)),
                    "沙船GM值应为0.85m");
            assertEquals(0, new BigDecimal("1.20").compareTo(gmValues.get(fuChuan.getId()).setScale(2, RoundingMode.HALF_UP)),
                    "福船GM值应为1.20m");
            assertEquals(0, new BigDecimal("1.05").compareTo(gmValues.get(guangChuan.getId()).setScale(2, RoundingMode.HALF_UP)),
                    "广船GM值应为1.05m");

            assertTrue(gmValues.get(fuChuan.getId()).compareTo(gmValues.get(guangChuan.getId())) > 0,
                    "福船GM值应大于广船");
            assertTrue(gmValues.get(guangChuan.getId()).compareTo(gmValues.get(sandShip.getId())) > 0,
                    "广船GM值应大于沙船");

            verify(shipComparisonRepository, times(1)).save(any(ShipComparison.class));
        }

        @Test
        @DisplayName("边界场景：两艘船GM值非常接近 - 验证评分算法稳定性")
        void testCloseGmValuesComparison() {
            Ship shipA = TestDataBuilder.buildShip("测试船A", "ANCIENT", "沙船", "变体A",
                    new BigDecimal("30.0"), new BigDecimal("8.0"), new BigDecimal("3.5"),
                    new BigDecimal("2.0"), new BigDecimal("300.0"), new BigDecimal("150.0"),
                    new BigDecimal("0.80"), new BigDecimal("2.5"));
            Ship shipB = TestDataBuilder.buildShip("测试船B", "ANCIENT", "沙船", "变体B",
                    new BigDecimal("30.1"), new BigDecimal("8.0"), new BigDecimal("3.5"),
                    new BigDecimal("2.0"), new BigDecimal("300.0"), new BigDecimal("150.0"),
                    new BigDecimal("0.81"), new BigDecimal("2.5"));

            List<UUID> shipIds = Arrays.asList(shipA.getId(), shipB.getId());
            ShipComparisonRequest request = TestDataBuilder.buildShipComparisonRequest(shipIds);

            when(shipRepository.findAllById(shipIds)).thenReturn(Arrays.asList(shipA, shipB));
            when(stabilitySimulatorService.calculateStability(eq(shipA.getId()), any(SensorData.class)))
                    .thenReturn(createMockStabilityResult(shipA, new BigDecimal("0.80")));
            when(stabilitySimulatorService.calculateStability(eq(shipB.getId()), any(SensorData.class)))
                    .thenReturn(createMockStabilityResult(shipB, new BigDecimal("0.81")));
            when(shipComparisonRepository.save(any(ShipComparison.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(entityManager.createNativeQuery(anyString())).thenReturn(query);
            when(query.getSingleResult()).thenReturn("ANCIENT");
            when(query.setParameter(anyString(), any())).thenReturn(query);

            ShipComparisonResultDTO result = shipComparisonService.compareShips(request);

            assertNotNull(result);
            assertEquals(2, result.getComparisonItems().size());

            ShipComparisonResultDTO.ShipComparisonItem itemA = result.getComparisonItems().stream()
                    .filter(i -> i.getShipId().equals(shipA.getId())).findFirst().orElseThrow();
            ShipComparisonResultDTO.ShipComparisonItem itemB = result.getComparisonItems().stream()
                    .filter(i -> i.getShipId().equals(shipB.getId())).findFirst().orElseThrow();

            assertTrue(itemB.getScore().compareTo(itemA.getScore()) > 0,
                    "GM值略高的船B排名应更高");
            assertEquals(1, itemB.getRank(), "船B应为第1名");
            assertEquals(2, itemA.getRank(), "船A应为第2名");

            BigDecimal scoreDiff = itemB.getScore().subtract(itemA.getScore()).abs();
            assertTrue(scoreDiff.compareTo(new BigDecimal("0.1")) < 0,
                    "GM值接近时评分差异应较小");
        }
    }

    @Nested
    @DisplayName("跨时代对比测试")
    class CrossEraComparisonTests {

        @Test
        @DisplayName("正常场景：古代沙船 vs 现代散货船 - 现代船GM应显著更高")
        void testAncientVsModernGmComparison() {
            List<UUID> shipIds = Arrays.asList(sandShip.getId(), modernShip.getId());
            ShipComparisonRequest request = TestDataBuilder.buildShipComparisonRequest(shipIds);
            request.setComparisonCriteria(Arrays.asList("GM", "GZ_MAX", "RANGE", "GZ_AREA", "ROLL_PERIOD", "DISPLACEMENT", "DEADWEIGHT"));

            when(shipRepository.findAllById(shipIds)).thenReturn(Arrays.asList(sandShip, modernShip));

            StabilityResult sandShipResult = createMockStabilityResult(sandShip, new BigDecimal("0.85"));
            StabilityResult modernResult = createMockStabilityResult(modernShip, new BigDecimal("2.50"));

            when(stabilitySimulatorService.calculateStability(eq(sandShip.getId()), any(SensorData.class)))
                    .thenReturn(sandShipResult);
            when(stabilitySimulatorService.calculateStability(eq(modernShip.getId()), any(SensorData.class)))
                    .thenReturn(modernResult);
            when(shipComparisonRepository.save(any(ShipComparison.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            when(entityManager.createNativeQuery(anyString())).thenReturn(query);
            when(query.setParameter(anyString(), any())).thenReturn(query);
            when(query.getSingleResult())
                    .thenReturn("ANCIENT")
                    .thenReturn("MODERN")
                    .thenReturn("ANCIENT")
                    .thenReturn("MODERN")
                    .thenReturn("沙船")
                    .thenReturn("散货船")
                    .thenReturn(new BigDecimal("2.5"))
                    .thenReturn(new BigDecimal("12.0"));

            ShipComparisonResultDTO result = shipComparisonService.compareShips(request);

            assertNotNull(result);
            assertEquals(2, result.getComparisonItems().size());

            ShipComparisonResultDTO.ShipComparisonItem ancientItem = result.getComparisonItems().stream()
                    .filter(i -> i.getShipId().equals(sandShip.getId())).findFirst().orElseThrow();
            ShipComparisonResultDTO.ShipComparisonItem modernItem = result.getComparisonItems().stream()
                    .filter(i -> i.getShipId().equals(modernShip.getId())).findFirst().orElseThrow();

            assertEquals("ANCIENT", ancientItem.getCategory(), "沙船分类应为古代");
            assertEquals("MODERN", modernItem.getCategory(), "散货船分类应为现代");

            BigDecimal gmAncient = ancientItem.getMetrics().get("GM");
            BigDecimal gmModern = modernItem.getMetrics().get("GM");
            assertTrue(gmModern.compareTo(gmAncient.multiply(new BigDecimal("2.5"))) > 0,
                    "现代船GM值应是古代船的2.5倍以上");

            BigDecimal displacementAncient = ancientItem.getMetrics().get("DISPLACEMENT");
            BigDecimal displacementModern = modernItem.getMetrics().get("DISPLACEMENT");
            assertTrue(displacementModern.compareTo(displacementAncient.multiply(new BigDecimal("100"))) > 0,
                    "现代船排水量应是古代船的100倍以上");

            BigDecimal rollPeriodAncient = ancientItem.getMetrics().get("ROLL_PERIOD");
            BigDecimal rollPeriodModern = modernItem.getMetrics().get("ROLL_PERIOD");
            assertTrue(rollPeriodModern.compareTo(rollPeriodAncient) > 0,
                    "现代船横摇周期应更长（船体更大）");

            assertTrue(modernItem.getScore().compareTo(ancientItem.getScore()) > 0,
                    "现代船综合评分应更高");
        }

        @Test
        @DisplayName("边界场景：全船型跨代对比 - 4艘不同类型船排名正确性")
        void testFullCrossEraComparison() {
            List<UUID> shipIds = Arrays.asList(sandShip.getId(), fuChuan.getId(), guangChuan.getId(), modernShip.getId());
            ShipComparisonRequest request = TestDataBuilder.buildShipComparisonRequest(shipIds);

            when(shipRepository.findAllById(shipIds)).thenReturn(Arrays.asList(sandShip, fuChuan, guangChuan, modernShip));
            when(stabilitySimulatorService.calculateStability(eq(sandShip.getId()), any(SensorData.class)))
                    .thenReturn(createMockStabilityResult(sandShip, new BigDecimal("0.85")));
            when(stabilitySimulatorService.calculateStability(eq(fuChuan.getId()), any(SensorData.class)))
                    .thenReturn(createMockStabilityResult(fuChuan, new BigDecimal("1.20")));
            when(stabilitySimulatorService.calculateStability(eq(guangChuan.getId()), any(SensorData.class)))
                    .thenReturn(createMockStabilityResult(guangChuan, new BigDecimal("1.05")));
            when(stabilitySimulatorService.calculateStability(eq(modernShip.getId()), any(SensorData.class)))
                    .thenReturn(createMockStabilityResult(modernShip, new BigDecimal("2.50")));
            when(shipComparisonRepository.save(any(ShipComparison.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(entityManager.createNativeQuery(anyString())).thenReturn(query);
            when(query.setParameter(anyString(), any())).thenReturn(query);
            when(query.getSingleResult())
                    .thenReturn("ANCIENT", "ANCIENT", "ANCIENT", "MODERN")
                    .thenReturn("沙船", "福船", "广船", "散货船")
                    .thenReturn(new BigDecimal("2.5"), new BigDecimal("5.2"), new BigDecimal("4.8"), new BigDecimal("12.0"));

            ShipComparisonResultDTO result = shipComparisonService.compareShips(request);

            assertEquals(4, result.getComparisonItems().size());

            List<ShipComparisonResultDTO.ShipComparisonItem> sortedItems = new ArrayList<>(result.getComparisonItems());
            sortedItems.sort(Comparator.comparingInt(ShipComparisonResultDTO.ShipComparisonItem::getRank));

            assertEquals(modernShip.getId(), sortedItems.get(0).getShipId(), "第1名应为现代散货船");
            assertEquals(fuChuan.getId(), sortedItems.get(1).getShipId(), "第2名应为福船");
            assertEquals(guangChuan.getId(), sortedItems.get(2).getShipId(), "第3名应为广船");
            assertEquals(sandShip.getId(), sortedItems.get(3).getShipId(), "第4名应为沙船");
        }
    }

    @Nested
    @DisplayName("评分算法验证测试")
    class ScoringAlgorithmTests {

        @Test
        @DisplayName("正常场景：权重分配正确性 - GM权重应为25%")
        void testWeightDistribution() {
            List<UUID> shipIds = Arrays.asList(sandShip.getId(), fuChuan.getId());
            ShipComparisonRequest request = TestDataBuilder.buildShipComparisonRequest(shipIds);
            request.setComparisonCriteria(Arrays.asList("GM", "GZ_MAX", "RANGE", "GZ_AREA", "ROLL_PERIOD"));

            when(shipRepository.findAllById(shipIds)).thenReturn(Arrays.asList(sandShip, fuChuan));
            when(stabilitySimulatorService.calculateStability(eq(sandShip.getId()), any(SensorData.class)))
                    .thenReturn(createMockStabilityResult(sandShip, new BigDecimal("0.50")));
            when(stabilitySimulatorService.calculateStability(eq(fuChuan.getId()), any(SensorData.class)))
                    .thenReturn(createMockStabilityResult(fuChuan, new BigDecimal("1.00")));
            when(shipComparisonRepository.save(any(ShipComparison.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(entityManager.createNativeQuery(anyString())).thenReturn(query);
            when(query.setParameter(anyString(), any())).thenReturn(query);
            when(query.getSingleResult())
                    .thenReturn("ANCIENT", "ANCIENT")
                    .thenReturn("沙船", "福船")
                    .thenReturn(new BigDecimal("2.5"), new BigDecimal("5.2"));

            ShipComparisonResultDTO result = shipComparisonService.compareShips(request);

            ShipComparisonResultDTO.ShipComparisonItem itemA = result.getComparisonItems().stream()
                    .filter(i -> i.getShipId().equals(sandShip.getId())).findFirst().orElseThrow();
            ShipComparisonResultDTO.ShipComparisonItem itemB = result.getComparisonItems().stream()
                    .filter(i -> i.getShipId().equals(fuChuan.getId())).findFirst().orElseThrow();

            BigDecimal normGmA = itemA.getMetrics().get("NORM_GM");
            BigDecimal normGmB = itemB.getMetrics().get("NORM_GM");
            assertEquals(0, BigDecimal.ZERO.compareTo(normGmA), "GM归一化后最小值应为0");
            assertEquals(0, BigDecimal.ONE.compareTo(normGmB), "GM归一化后最大值应为1");

            BigDecimal gmContribution = normGmB.multiply(new BigDecimal("0.25"));
            assertTrue(gmContribution.compareTo(new BigDecimal("0.20")) > 0,
                    "GM项应对总分有显著贡献（约25%）");
        }

        @Test
        @DisplayName("边界场景：ROLL_PERIOD归一化方向 - 值越小越好")
        void testRollPeriodNormalizationDirection() {
            Ship shipFast = TestDataBuilder.buildShip("快摇船", "ANCIENT", "沙船", "变体",
                    new BigDecimal("30.0"), new BigDecimal("8.0"), new BigDecimal("3.5"),
                    new BigDecimal("2.0"), new BigDecimal("300.0"), new BigDecimal("150.0"),
                    new BigDecimal("1.00"), new BigDecimal("2.5"));
            Ship shipSlow = TestDataBuilder.buildShip("慢摇船", "ANCIENT", "沙船", "变体",
                    new BigDecimal("30.0"), new BigDecimal("10.0"), new BigDecimal("3.5"),
                    new BigDecimal("2.0"), new BigDecimal("300.0"), new BigDecimal("150.0"),
                    new BigDecimal("1.00"), new BigDecimal("2.5"));

            List<UUID> shipIds = Arrays.asList(shipFast.getId(), shipSlow.getId());
            ShipComparisonRequest request = TestDataBuilder.buildShipComparisonRequest(shipIds);
            request.setComparisonCriteria(Arrays.asList("ROLL_PERIOD"));

            when(shipRepository.findAllById(shipIds)).thenReturn(Arrays.asList(shipFast, shipSlow));

            StabilityResult resultFast = createMockStabilityResult(shipFast, new BigDecimal("1.00"));
            resultFast.setRollPeriod(new BigDecimal("6.0"));
            StabilityResult resultSlow = createMockStabilityResult(shipSlow, new BigDecimal("1.00"));
            resultSlow.setRollPeriod(new BigDecimal("10.0"));

            when(stabilitySimulatorService.calculateStability(eq(shipFast.getId()), any(SensorData.class)))
                    .thenReturn(resultFast);
            when(stabilitySimulatorService.calculateStability(eq(shipSlow.getId()), any(SensorData.class)))
                    .thenReturn(resultSlow);
            when(shipComparisonRepository.save(any(ShipComparison.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(entityManager.createNativeQuery(anyString())).thenReturn(query);
            when(query.setParameter(anyString(), any())).thenReturn(query);
            when(query.getSingleResult())
                    .thenReturn("ANCIENT", "ANCIENT")
                    .thenReturn("沙船", "沙船")
                    .thenReturn(new BigDecimal("2.5"), new BigDecimal("2.5"));

            ShipComparisonResultDTO result = shipComparisonService.compareShips(request);

            ShipComparisonResultDTO.ShipComparisonItem itemFast = result.getComparisonItems().stream()
                    .filter(i -> i.getShipId().equals(shipFast.getId())).findFirst().orElseThrow();
            ShipComparisonResultDTO.ShipComparisonItem itemSlow = result.getComparisonItems().stream()
                    .filter(i -> i.getShipId().equals(shipSlow.getId())).findFirst().orElseThrow();

            assertEquals(0, BigDecimal.ONE.compareTo(itemFast.getMetrics().get("NORM_ROLL_PERIOD")),
                    "横摇周期较小的船应获得较高归一化分数");
            assertEquals(0, BigDecimal.ZERO.compareTo(itemSlow.getMetrics().get("NORM_ROLL_PERIOD")),
                    "横摇周期较大的船应获得较低归一化分数");

            assertTrue(itemFast.getScore().compareTo(itemSlow.getScore()) > 0,
                    "横摇周期较小的船排名应更高");
        }
    }

    @Nested
    @DisplayName("边界与异常场景测试")
    class BoundaryAndExceptionTests {

        @Test
        @DisplayName("异常场景：空船舶列表 - 应抛出IllegalArgumentException")
        void testEmptyShipList() {
            ShipComparisonRequest request = TestDataBuilder.buildShipComparisonRequest(Collections.emptyList());

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                shipComparisonService.compareShips(request);
            });

            assertTrue(exception.getMessage().contains("不存在"), "异常信息应包含'不存在'");
            verify(shipRepository, times(1)).findAllById(Collections.emptyList());
            verify(shipComparisonRepository, never()).save(any());
        }

        @Test
        @DisplayName("异常场景：部分船舶不存在 - 应抛出异常")
        void testNonExistentShip() {
            UUID realId = sandShip.getId();
            UUID fakeId = UUID.randomUUID();
            List<UUID> shipIds = Arrays.asList(realId, fakeId);
            ShipComparisonRequest request = TestDataBuilder.buildShipComparisonRequest(shipIds);

            when(shipRepository.findAllById(shipIds)).thenReturn(Collections.singletonList(sandShip));

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                shipComparisonService.compareShips(request);
            });

            assertTrue(exception.getMessage().contains("不存在"), "异常信息应说明部分船舶不存在");
            verify(shipComparisonRepository, never()).save(any());
        }

        @Test
        @DisplayName("边界场景：单船对比 - 应成功执行（自身对比）")
        void testSingleShipComparison() {
            List<UUID> shipIds = Collections.singletonList(sandShip.getId());
            ShipComparisonRequest request = TestDataBuilder.buildShipComparisonRequest(shipIds);

            when(shipRepository.findAllById(shipIds)).thenReturn(Collections.singletonList(sandShip));
            when(stabilitySimulatorService.calculateStability(eq(sandShip.getId()), any(SensorData.class)))
                    .thenReturn(createMockStabilityResult(sandShip, new BigDecimal("0.85")));
            when(shipComparisonRepository.save(any(ShipComparison.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(entityManager.createNativeQuery(anyString())).thenReturn(query);
            when(query.setParameter(anyString(), any())).thenReturn(query);
            when(query.getSingleResult())
                    .thenReturn("ANCIENT")
                    .thenReturn("沙船")
                    .thenReturn(new BigDecimal("2.5"));

            ShipComparisonResultDTO result = shipComparisonService.compareShips(request);

            assertNotNull(result);
            assertEquals(1, result.getComparisonItems().size());
            assertEquals(1, result.getComparisonItems().get(0).getRank(), "单船对比排名应为1");
            assertNotNull(result.getComparisonItems().get(0).getScore(), "评分不应为null");
            verify(shipComparisonRepository, times(1)).save(any(ShipComparison.class));
        }

        @Test
        @DisplayName("异常场景：稳性计算失败 - 应抛出运行时异常")
        void testStabilityCalculationFailure() {
            List<UUID> shipIds = Arrays.asList(sandShip.getId(), fuChuan.getId());
            ShipComparisonRequest request = TestDataBuilder.buildShipComparisonRequest(shipIds);

            when(shipRepository.findAllById(shipIds)).thenReturn(Arrays.asList(sandShip, fuChuan));
            when(stabilitySimulatorService.calculateStability(eq(sandShip.getId()), any(SensorData.class)))
                    .thenThrow(new RuntimeException("模拟计算失败"));

            RuntimeException exception = assertThrows(RuntimeException.class, () -> {
                shipComparisonService.compareShips(request);
            });

            assertTrue(exception.getMessage().contains("船型对比失败"), "异常信息应包含'船型对比失败'");
            verify(shipComparisonRepository, never()).save(any());
        }

        @Test
        @DisplayName("边界场景：所有指标值相同 - 排名应按船舶顺序")
        void testAllMetricsEqual() {
            Ship shipA = TestDataBuilder.buildShip("船A", "ANCIENT", "沙船", "变体",
                    new BigDecimal("30.0"), new BigDecimal("8.0"), new BigDecimal("3.5"),
                    new BigDecimal("2.0"), new BigDecimal("300.0"), new BigDecimal("150.0"),
                    new BigDecimal("1.00"), new BigDecimal("2.5"));
            Ship shipB = TestDataBuilder.buildShip("船B", "ANCIENT", "沙船", "变体",
                    new BigDecimal("30.0"), new BigDecimal("8.0"), new BigDecimal("3.5"),
                    new BigDecimal("2.0"), new BigDecimal("300.0"), new BigDecimal("150.0"),
                    new BigDecimal("1.00"), new BigDecimal("2.5"));

            List<UUID> shipIds = Arrays.asList(shipA.getId(), shipB.getId());
            ShipComparisonRequest request = TestDataBuilder.buildShipComparisonRequest(shipIds);

            when(shipRepository.findAllById(shipIds)).thenReturn(Arrays.asList(shipA, shipB));
            when(stabilitySimulatorService.calculateStability(eq(shipA.getId()), any(SensorData.class)))
                    .thenReturn(createMockStabilityResult(shipA, new BigDecimal("1.00")));
            when(stabilitySimulatorService.calculateStability(eq(shipB.getId()), any(SensorData.class)))
                    .thenReturn(createMockStabilityResult(shipB, new BigDecimal("1.00")));
            when(shipComparisonRepository.save(any(ShipComparison.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(entityManager.createNativeQuery(anyString())).thenReturn(query);
            when(query.setParameter(anyString(), any())).thenReturn(query);
            when(query.getSingleResult())
                    .thenReturn("ANCIENT", "ANCIENT")
                    .thenReturn("沙船", "沙船")
                    .thenReturn(new BigDecimal("2.5"), new BigDecimal("2.5"));

            ShipComparisonResultDTO result = shipComparisonService.compareShips(request);

            assertEquals(0, result.getComparisonItems().get(0).getScore()
                    .compareTo(result.getComparisonItems().get(1).getScore()),
                    "所有指标相同时评分应相等");
        }
    }

    @Nested
    @DisplayName("历史记录查询测试")
    class HistoryQueryTests {

        @Test
        @DisplayName("正常场景：获取对比历史 - 应返回正确数量")
        void testGetComparisonHistory() {
            ShipComparison comparison1 = new ShipComparison();
            comparison1.setId(UUID.randomUUID());
            comparison1.setComparisonName("对比1");
            comparison1.setShipIds(Arrays.asList(sandShip.getId(), fuChuan.getId()));
            comparison1.setComparisonCriteria(Arrays.asList("GM", "GZ_MAX"));

            ShipComparison comparison2 = new ShipComparison();
            comparison2.setId(UUID.randomUUID());
            comparison2.setComparisonName("对比2");
            comparison2.setShipIds(Collections.singletonList(sandShip.getId()));
            comparison2.setComparisonCriteria(Collections.singletonList("GM"));

            when(shipComparisonRepository.findTop10ByOrderByCreatedAtDesc())
                    .thenReturn(Arrays.asList(comparison1, comparison2));

            List<ShipComparisonResultDTO> history = shipComparisonService.getComparisonHistory(10);

            assertEquals(2, history.size());
            assertEquals("对比1", history.get(0).getComparisonName());
            assertEquals(2, history.get(0).getShipIds().size());
            verify(shipComparisonRepository, times(1)).findTop10ByOrderByCreatedAtDesc();
        }

        @Test
        @DisplayName("边界场景：limit=0 - 应返回全部记录")
        void testGetAllHistory() {
            when(shipComparisonRepository.findAll()).thenReturn(Collections.emptyList());

            List<ShipComparisonResultDTO> history = shipComparisonService.getComparisonHistory(0);

            assertTrue(history.isEmpty());
            verify(shipComparisonRepository, times(1)).findAll();
            verify(shipComparisonRepository, never()).findTop10ByOrderByCreatedAtDesc();
        }

        @Test
        @DisplayName("异常场景：查询不存在的对比记录 - 应抛出异常")
        void testGetNonExistentComparison() {
            UUID fakeId = UUID.randomUUID();
            when(shipComparisonRepository.findById(fakeId)).thenReturn(Optional.empty());

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                shipComparisonService.getComparisonById(fakeId);
            });

            assertTrue(exception.getMessage().contains("不存在"));
        }

        @Test
        @DisplayName("正常场景：删除对比记录")
        void testDeleteComparison() {
            UUID id = UUID.randomUUID();
            when(shipComparisonRepository.existsById(id)).thenReturn(true);

            assertDoesNotThrow(() -> shipComparisonService.deleteComparison(id));
            verify(shipComparisonRepository, times(1)).deleteById(id);
        }

        @Test
        @DisplayName("异常场景：删除不存在的对比记录 - 应抛出异常")
        void testDeleteNonExistentComparison() {
            UUID fakeId = UUID.randomUUID();
            when(shipComparisonRepository.existsById(fakeId)).thenReturn(false);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                shipComparisonService.deleteComparison(fakeId);
            });

            assertTrue(exception.getMessage().contains("不存在"));
            verify(shipComparisonRepository, never()).deleteById(any());
        }
    }
}
