package com.sandship.stability.vr_loading.strategy;

import com.sandship.stability.entity.CargoHold;
import com.sandship.stability.entity.Ship;
import com.sandship.stability.util.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("StrategyEngine 装载策略引擎测试")
class StrategyEngineTest {

    private StrategyEngine engine;

    @BeforeEach
    void setUp() {
        engine = new StrategyEngine();
    }

    @Nested
    @DisplayName("默认装载量计算测试")
    class DefaultLoadWeightTests {

        @Test
        @DisplayName("大舱室：默认装载剩余容量的20%")
        void largeHoldTwentyPercent() {
            double remaining = 500.0;
            double available = 999.0;
            double result = engine.calculateDefaultLoadWeight(remaining, available);
            assertEquals(remaining * 0.2, result, 1e-6);
        }

        @Test
        @DisplayName("小舱室：至少装5t")
        void smallHoldMinimumFiveTons() {
            double remaining = 10.0;
            double available = 999.0;
            double result = engine.calculateDefaultLoadWeight(remaining, available);
            assertEquals(5.0, result, 1e-6);
        }

        @Test
        @DisplayName("容量不足5t时按容量装")
        void capacityLessThanFive() {
            double remaining = 3.0;
            double available = 999.0;
            double result = engine.calculateDefaultLoadWeight(remaining, available);
            assertEquals(3.0, result, 1e-6);
        }

        @Test
        @DisplayName("可用量不足时按可用量装")
        void limitedByAvailable() {
            double remaining = 100.0;
            double available = 8.0;
            double result = engine.calculateDefaultLoadWeight(remaining, available);
            assertEquals(8.0, result, 1e-6);
        }

        @Test
        @DisplayName("容量为0时返回0")
        void zeroCapacityReturnsZero() {
            double result = engine.calculateDefaultLoadWeight(0.0, 100.0);
            assertEquals(0.0, result, 1e-6);
        }

        @Test
        @DisplayName("负容量返回0")
        void negativeCapacityReturnsZero() {
            double result = engine.calculateDefaultLoadWeight(-10.0, 100.0);
            assertEquals(0.0, result, 1e-6);
        }

        @Test
        @DisplayName("可用量为0返回0")
        void zeroAvailableReturnsZero() {
            double result = engine.calculateDefaultLoadWeight(100.0, 0.0);
            assertEquals(0.0, result, 1e-6);
        }
    }

    @Nested
    @DisplayName("重心变化计算测试")
    class CGChangeTests {

        @Test
        @DisplayName("加装载后重心加权平均")
        void cgWeightedAverage() {
            double currentCgZ = 2.0;
            double currentWeight = 100.0;
            double addWeight = 50.0;
            double holdCgZ = 4.0;

            double[] result = engine.calculateCGChange(0, 0, currentCgZ, currentWeight,
                    addWeight, 0, 0, holdCgZ);

            double expectedZ = (currentCgZ * currentWeight + holdCgZ * addWeight)
                    / (currentWeight + addWeight);
            assertEquals(expectedZ, result[2], 1e-3);
        }

        @Test
        @DisplayName("加装载0重量重心不变")
        void zeroAddWeightCgUnchanged() {
            double currentCgZ = 2.0;
            double[] result = engine.calculateCGChange(0, 0, currentCgZ, 100.0,
                    0.0, 0, 0, 4.0);
            assertEquals(currentCgZ, result[2], 1e-6);
        }

        @Test
        @DisplayName("加装载负重量重心不变")
        void negativeAddWeightCgUnchanged() {
            double currentCgZ = 2.0;
            double[] result = engine.calculateCGChange(0, 0, currentCgZ, 100.0,
                    -10.0, 0, 0, 4.0);
            assertEquals(currentCgZ, result[2], 1e-6);
        }

        @Test
        @DisplayName("从零开始加装载重心等于货舱重心")
        void fromZeroCgEqualsHoldCg() {
            double[] result = engine.calculateCGChange(0, 0, 0, 0.0,
                    50.0, 1.0, 2.0, 3.0);
            assertEquals(1.0, result[0], 1e-6);
            assertEquals(2.0, result[1], 1e-6);
            assertEquals(3.0, result[2], 1e-6);
        }

        @Test
        @DisplayName("低货舱装载降低重心")
        void lowHoldLowersCg() {
            double currentCgZ = 4.0;
            double[] result = engine.calculateCGChange(0, 0, currentCgZ, 100.0,
                    50.0, 0, 0, 1.0);
            assertTrue(result[2] < currentCgZ);
        }

        @Test
        @DisplayName("高货舱装载升高重心")
        void highHoldRaisesCg() {
            double currentCgZ = 2.0;
            double[] result = engine.calculateCGChange(0, 0, currentCgZ, 100.0,
                    50.0, 0, 0, 5.0);
            assertTrue(result[2] > currentCgZ);
        }
    }

    @Nested
    @DisplayName("推荐装载方案测试")
    class RecommendLoadingTests {

        @Test
        @DisplayName("推荐方案总重量=载重吨的70%")
        void recommendationTotalIsSeventyPercent() {
            Ship ship = TestDataBuilder.buildSandShip();
            List<CargoHold> holds = buildTestHolds(5);

            Map<UUID, Double> recommendation = engine.recommendLoading(ship, holds);

            double total = recommendation.values().stream().mapToDouble(Double::doubleValue).sum();
            double target = ship.getDeadweightTons().doubleValue() * 0.7;

            assertTrue(total > 0, "推荐方案应有装载量");
            assertTrue(total <= target * 1.01, "总装载量不应超过目标的1%偏差");
        }

        @Test
        @DisplayName("空货舱列表返回空推荐")
        void emptyHoldsEmptyRecommendation() {
            Ship ship = TestDataBuilder.buildSandShip();
            Map<UUID, Double> recommendation = engine.recommendLoading(ship, new ArrayList<>());
            assertTrue(recommendation.isEmpty());
        }

        @Test
        @DisplayName("null货舱列表返回空推荐")
        void nullHoldsEmptyRecommendation() {
            Ship ship = TestDataBuilder.buildSandShip();
            Map<UUID, Double> recommendation = engine.recommendLoading(ship, null);
            assertTrue(recommendation.isEmpty());
        }

        @Test
        @DisplayName("载重吨为0返回空推荐")
        void zeroDeadweightEmptyRecommendation() {
            Ship ship = TestDataBuilder.buildSandShip();
            ship.setDeadweightTons(BigDecimal.ZERO);
            List<CargoHold> holds = buildTestHolds(3);

            Map<UUID, Double> recommendation = engine.recommendLoading(ship, holds);
            assertTrue(recommendation.isEmpty());
        }

        @Test
        @DisplayName("低舱装载量 > 高舱装载量（重货放低舱原则）")
        void lowHoldsHaveMoreWeight() {
            List<CargoHold> holds = buildTestHolds(4);
            holds.get(0).setCenterGravityZ(BigDecimal.valueOf(1.0));
            holds.get(1).setCenterGravityZ(BigDecimal.valueOf(1.5));
            holds.get(2).setCenterGravityZ(BigDecimal.valueOf(2.5));
            holds.get(3).setCenterGravityZ(BigDecimal.valueOf(3.0));

            Ship ship = TestDataBuilder.buildSandShip();
            Map<UUID, Double> recommendation = engine.recommendLoading(ship, holds);

            double lowTotal = recommendation.getOrDefault(holds.get(0).getId(), 0.0)
                    + recommendation.getOrDefault(holds.get(1).getId(), 0.0);
            double highTotal = recommendation.getOrDefault(holds.get(2).getId(), 0.0)
                    + recommendation.getOrDefault(holds.get(3).getId(), 0.0);

            assertTrue(lowTotal > highTotal, "低舱装载量应大于高舱");
        }

        @Test
        @DisplayName("每个货舱装载量不超过其最大载重")
        void noHoldExceedsMaxWeight() {
            Ship ship = TestDataBuilder.buildSandShip();
            List<CargoHold> holds = buildTestHolds(5);

            Map<UUID, Double> recommendation = engine.recommendLoading(ship, holds);

            for (CargoHold hold : holds) {
                Double assigned = recommendation.get(hold.getId());
                if (assigned != null) {
                    assertTrue(assigned <= hold.getMaxWeight().doubleValue() + 1e-6,
                            "货舱 " + hold.getHoldName() + " 超装");
                }
            }
        }

        @Test
        @DisplayName("液舱不参与装载推荐")
        void tanksExcludedFromRecommendation() {
            List<CargoHold> holds = buildTestHolds(3);
            CargoHold tank = new CargoHold();
            tank.setId(UUID.randomUUID());
            tank.setHoldName("燃油舱");
            tank.setIsTank(true);
            tank.setMaxWeight(BigDecimal.valueOf(50.0));
            tank.setCenterGravityZ(BigDecimal.valueOf(0.5));
            holds.add(tank);

            Ship ship = TestDataBuilder.buildSandShip();
            Map<UUID, Double> recommendation = engine.recommendLoading(ship, holds);

            assertFalse(recommendation.containsKey(tank.getId()),
                    "液舱不应在推荐方案中");
        }
    }

    private List<CargoHold> buildTestHolds(int count) {
        List<CargoHold> holds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CargoHold hold = new CargoHold();
            hold.setId(UUID.randomUUID());
            hold.setHoldNumber(i + 1);
            hold.setHoldName("" + (char) ('A' + i) + "货舱");
            hold.setHoldType("DRY_CARGO");
            hold.setIsTank(false);
            hold.setMaxWeight(BigDecimal.valueOf(80.0));
            hold.setMaxVolume(BigDecimal.valueOf(200.0));
            hold.setCenterGravityX(BigDecimal.valueOf(5.0 + i * 3.0));
            hold.setCenterGravityY(BigDecimal.ZERO);
            hold.setCenterGravityZ(BigDecimal.valueOf(1.5 + (i % 2) * 1.5));
            holds.add(hold);
        }
        return holds;
    }
}
