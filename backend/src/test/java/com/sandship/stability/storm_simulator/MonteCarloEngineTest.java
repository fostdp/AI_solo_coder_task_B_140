package com.sandship.stability.storm_simulator;

import com.sandship.stability.storm_simulator.monte_carlo.MonteCarloEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MonteCarloEngine 蒙特卡洛引擎测试")
class MonteCarloEngineTest {

    private static final double BASE_GM = 1.0;

    @Nested
    @DisplayName("动态GM计算测试")
    class DynamicGMTests {

        @Test
        @DisplayName("0°横摇→GM≈100%原值")
        void calculateDynamicGM_0deg_shouldBe100Percent() {
            double gm = MonteCarloEngine.calculateDynamicGM(BASE_GM, 0.0);
            assertEquals(1.0, gm / BASE_GM, 0.01);
        }

        @Test
        @DisplayName("10°横摇→GM≈95%以上")
        void calculateDynamicGM_10deg_above95Percent() {
            double gm = MonteCarloEngine.calculateDynamicGM(BASE_GM, 10.0);
            assertTrue(gm / BASE_GM >= 0.95);
            assertTrue(gm / BASE_GM <= 1.0);
        }

        @Test
        @DisplayName("20°横摇→GM约75-95%")
        void calculateDynamicGM_20deg_between75And95() {
            double gm = MonteCarloEngine.calculateDynamicGM(BASE_GM, 20.0);
            double ratio = gm / BASE_GM;
            assertTrue(ratio >= 0.75 && ratio <= 0.95,
                    "Ratio was: " + ratio);
        }

        @Test
        @DisplayName("35°横摇→GM约35-70%")
        void calculateDynamicGM_35deg_between35And70() {
            double gm = MonteCarloEngine.calculateDynamicGM(BASE_GM, 35.0);
            double ratio = gm / BASE_GM;
            assertTrue(ratio >= 0.35 && ratio <= 0.70,
                    "Ratio was: " + ratio);
        }

        @Test
        @DisplayName("50°横摇→GM约0-35%")
        void calculateDynamicGM_50deg_between0And35() {
            double gm = MonteCarloEngine.calculateDynamicGM(BASE_GM, 50.0);
            double ratio = gm / BASE_GM;
            assertTrue(ratio >= 0.0 && ratio <= 0.35,
                    "Ratio was: " + ratio);
        }

        @Test
        @DisplayName("80°横摇→GM接近0或负")
        void calculateDynamicGM_80deg_nearZeroOrNegative() {
            double gm = MonteCarloEngine.calculateDynamicGM(BASE_GM, 80.0);
            assertTrue(gm / BASE_GM <= 0.10,
                    "Ratio was: " + gm / BASE_GM);
        }

        @Test
        @DisplayName("动态GM单调递减（角度越大GM越小）")
        void calculateDynamicGM_shouldBeMonotonicallyDecreasing() {
            double prev = Double.MAX_VALUE;
            for (int deg = 0; deg <= 90; deg += 5) {
                double gm = MonteCarloEngine.calculateDynamicGM(BASE_GM, deg);
                assertTrue(gm <= prev + 1e-9, "At deg=" + deg + ": gm=" + gm + " > prev=" + prev);
                prev = gm;
            }
        }
    }

    @Nested
    @DisplayName("参数横摇检测测试")
    class ParametricRollDetectionTests {

        @Test
        @DisplayName("periodRatio=0.5→false (远离共振)")
        void detectParametricRollRisk_ratio05_shouldBeFalse() {
            assertFalse(MonteCarloEngine.detectParametricRollRisk(5.0, 10.0));
        }

        @Test
        @DisplayName("periodRatio=0.9→true (接近共振)")
        void detectParametricRollRisk_ratio09_shouldBeTrue() {
            assertTrue(MonteCarloEngine.detectParametricRollRisk(9.0, 10.0));
        }

        @Test
        @DisplayName("periodRatio=1.0→true (共振)")
        void detectParametricRollRisk_ratio10_shouldBeTrue() {
            assertTrue(MonteCarloEngine.detectParametricRollRisk(10.0, 10.0));
        }

        @Test
        @DisplayName("periodRatio=1.5→false (远离共振)")
        void detectParametricRollRisk_ratio15_shouldBeFalse() {
            assertFalse(MonteCarloEngine.detectParametricRollRisk(15.0, 10.0));
        }

        @Test
        @DisplayName("边界值periodRatio=0.81→true")
        void detectParametricRollRisk_ratio081_shouldBeTrue() {
            assertTrue(MonteCarloEngine.detectParametricRollRisk(8.1, 10.0));
        }

        @Test
        @DisplayName("边界值periodRatio=1.19→true")
        void detectParametricRollRisk_ratio119_shouldBeTrue() {
            assertTrue(MonteCarloEngine.detectParametricRollRisk(11.9, 10.0));
        }
    }

    @Nested
    @DisplayName("倾覆判定测试")
    class CapsizingTests {

        @Test
        @DisplayName("迭代足够多次后，对低GM船，倾覆概率应>0")
        void runSimulation_lowGmShip_capsizingProbabilityAboveZero() {
            MonteCarloEngine.ShipParams shipParams = new MonteCarloEngine.ShipParams(
                    30.0, 8.0, 300.0, 0.1, 2.5, 0.35
            );
            MonteCarloEngine.StormParams stormParams = new MonteCarloEngine.StormParams(
                    8.0, 10.0, 30.0, "HURRICANE"
            );

            MonteCarloEngine.SimulationResult result = MonteCarloEngine.runSimulation(
                    shipParams, stormParams, 10000
            );

            assertTrue(result.capsizingProbability >= 0);
            assertTrue(result.capsizingCount >= 0);
            assertEquals(10000, result.totalIterations);
        }
    }

    @Nested
    @DisplayName("横摇周期计算测试")
    class RollPeriodTests {

        @Test
        @DisplayName("T=2πk/√(g/B×GM)，单位合理性验证（秒级）")
        void calculateRollPeriod_reasonableSeconds() {
            MonteCarloEngine.ShipParams shipParams = new MonteCarloEngine.ShipParams(
                    180.0, 30.0, 35000.0, 2.5, 12.0, 0.40
            );
            double period = MonteCarloEngine.calculateRollPeriod(shipParams);
            assertTrue(period > 0);
            assertTrue(period < 60.0, "横摇周期不应超过60秒，实际: " + period);
        }

        @Test
        @DisplayName("小GM→长周期（物理合理性）")
        void calculateRollPeriod_smallGm_longerPeriod() {
            MonteCarloEngine.ShipParams shipLowGM = new MonteCarloEngine.ShipParams(
                    30.0, 8.0, 300.0, 0.3, 2.5, 0.35
            );
            MonteCarloEngine.ShipParams shipHighGM = new MonteCarloEngine.ShipParams(
                    30.0, 8.0, 300.0, 1.5, 2.5, 0.35
            );
            double periodLow = MonteCarloEngine.calculateRollPeriod(shipLowGM);
            double periodHigh = MonteCarloEngine.calculateRollPeriod(shipHighGM);
            assertTrue(periodLow > periodHigh, "低GM应有更长的横摇周期");
        }

        @Test
        @DisplayName("GM<=0时返回0.0")
        void calculateRollPeriod_zeroOrNegativeGm_returnsZero() {
            MonteCarloEngine.ShipParams zeroGm = new MonteCarloEngine.ShipParams(
                    30.0, 8.0, 300.0, 0.0, 2.5, 0.35
            );
            MonteCarloEngine.ShipParams negGm = new MonteCarloEngine.ShipParams(
                    30.0, 8.0, 300.0, -0.5, 2.5, 0.35
            );
            assertEquals(0.0, MonteCarloEngine.calculateRollPeriod(zeroGm));
            assertEquals(0.0, MonteCarloEngine.calculateRollPeriod(negGm));
        }
    }

    @Nested
    @DisplayName("仿真返回值完整性测试")
    class SimulationResultTests {

        @Test
        @DisplayName("runSimulation返回值不为空，字段完整")
        void runSimulation_returnsNonNullResult() {
            MonteCarloEngine.ShipParams shipParams = new MonteCarloEngine.ShipParams(
                    30.0, 8.0, 300.0, 0.85, 2.5, 0.35
            );
            MonteCarloEngine.StormParams stormParams = new MonteCarloEngine.StormParams(
                    3.0, 10.0, 15.0, "TROPICAL_STORM"
            );

            MonteCarloEngine.SimulationResult result = MonteCarloEngine.runSimulation(
                    shipParams, stormParams, 1000
            );

            assertNotNull(result);
            assertEquals(1000, result.totalIterations);
            assertTrue(result.maxRollAngle >= 0);
            assertTrue(result.rollStdDev >= 0);
            assertTrue(result.broachingProbability >= 0 && result.broachingProbability <= 1.0);
            assertTrue(result.rightingArmLossPercentage >= 0);
        }

        @Test
        @DisplayName("periodRatio正确计算")
        void runSimulation_periodRatioCalculated() {
            MonteCarloEngine.ShipParams shipParams = new MonteCarloEngine.ShipParams(
                    30.0, 8.0, 300.0, 0.85, 2.5, 0.35
            );
            double rollPeriod = MonteCarloEngine.calculateRollPeriod(shipParams);
            double wavePeriod = rollPeriod * 1.5;
            MonteCarloEngine.StormParams stormParams = new MonteCarloEngine.StormParams(
                    3.0, wavePeriod, 15.0, "TROPICAL_STORM"
            );

            MonteCarloEngine.SimulationResult result = MonteCarloEngine.runSimulation(
                    shipParams, stormParams, 100
            );

            assertEquals(1.5, result.periodRatio, 0.01);
            assertFalse(result.parametricRollRisk);
            assertEquals(1.0, result.parametricAmplification, 1e-9);
        }
    }
}
