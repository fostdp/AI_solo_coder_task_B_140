package com.sandship.stability.storm_simulator.monte_carlo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MonteCarloEngine 蒙特卡洛引擎测试")
class MonteCarloEngineTest {

    private MonteCarloEngine.ShipParams shipParams;
    private MonteCarloEngine.StormParams stormParams;

    @BeforeEach
    void setUp() {
        shipParams = new MonteCarloEngine.ShipParams();
        shipParams.baseGM = 1.20;
        shipParams.breadth = 8.0;
        shipParams.length = 35.0;
        shipParams.displacement = 620.0;
        shipParams.rollRadiusCoefficient = 0.35;
        shipParams.bowHeight = 3.0;

        stormParams = new MonteCarloEngine.StormParams();
        stormParams.waveHeight = 3.0;
        stormParams.wavePeriod = 8.0;
        stormParams.windSpeed = 15.0;
    }

    @Nested
    @DisplayName("横摇周期计算测试")
    class RollPeriodTests {

        @Test
        @DisplayName("横摇周期公式正确: T = 2π·k·B/√(g·GM)")
        void rollPeriodFormula() {
            double T = MonteCarloEngine.calculateRollPeriod(shipParams);

            double k = shipParams.rollRadiusCoefficient;
            double B = shipParams.breadth;
            double GM = shipParams.baseGM;
            double g = 9.81;
            double expected = 2 * Math.PI * k * B / Math.sqrt(g * GM);

            assertEquals(expected, T, 1e-6);
        }

        @Test
        @DisplayName("GM越大，横摇周期越短")
        void higherGMGivesShorterPeriod() {
            MonteCarloEngine.ShipParams lowGM = new MonteCarloEngine.ShipParams();
            lowGM.baseGM = 0.5;
            lowGM.breadth = 8.0;
            lowGM.rollRadiusCoefficient = 0.35;

            MonteCarloEngine.ShipParams highGM = new MonteCarloEngine.ShipParams();
            highGM.baseGM = 1.5;
            highGM.breadth = 8.0;
            highGM.rollRadiusCoefficient = 0.35;

            double tLow = MonteCarloEngine.calculateRollPeriod(lowGM);
            double tHigh = MonteCarloEngine.calculateRollPeriod(highGM);

            assertTrue(tLow > tHigh, "GM大的船横摇周期更短");
        }

        @Test
        @DisplayName("船宽越大，横摇周期越长")
        void widerBeamGivesLongerPeriod() {
            MonteCarloEngine.ShipParams narrow = new MonteCarloEngine.ShipParams();
            narrow.baseGM = 1.0;
            narrow.breadth = 6.0;
            narrow.rollRadiusCoefficient = 0.35;

            MonteCarloEngine.ShipParams wide = new MonteCarloEngine.ShipParams();
            wide.baseGM = 1.0;
            wide.breadth = 10.0;
            wide.rollRadiusCoefficient = 0.35;

            double tNarrow = MonteCarloEngine.calculateRollPeriod(narrow);
            double tWide = MonteCarloEngine.calculateRollPeriod(wide);

            assertTrue(tWide > tNarrow, "宽船横摇周期更长");
        }
    }

    @Nested
    @DisplayName("动态GM计算测试")
    class DynamicGMTests {

        @Test
        @DisplayName("0°横摇角时GM基本不变")
        void zeroRollGmNearlyUnchanged() {
            double dynamicGM = MonteCarloEngine.calculateDynamicGM(1.0, 0.0);
            assertTrue(dynamicGM > 0.97);
            assertTrue(dynamicGM <= 1.0);
        }

        @Test
        @DisplayName("15°横摇角时GM减少<10%")
        void smallRollGmReduction() {
            double dynamicGM = MonteCarloEngine.calculateDynamicGM(1.0, 15.0);
            assertTrue(dynamicGM > 0.85, "15°时GM仍应>85%");
            assertTrue(dynamicGM < 1.0);
        }

        @Test
        @DisplayName("30°横摇角时GM显著减少")
        void midRollSignificantReduction() {
            double dynamicGM = MonteCarloEngine.calculateDynamicGM(1.0, 30.0);
            assertTrue(dynamicGM > 0.55, "30°时GM应>55%");
            assertTrue(dynamicGM < 0.8, "30°时GM应<80%");
        }

        @Test
        @DisplayName("45°横摇角时GM大幅减少")
        void largeRollMajorReduction() {
            double dynamicGM = MonteCarloEngine.calculateDynamicGM(1.0, 45.0);
            assertTrue(dynamicGM > 0.15, "45°时GM应>15%");
            assertTrue(dynamicGM < 0.5, "45°时GM应<50%");
        }

        @Test
        @DisplayName("动态GM不低于-0.5m")
        void dynamicGMHasLowerBound() {
            double dynamicGM = MonteCarloEngine.calculateDynamicGM(1.0, 90.0);
            assertTrue(dynamicGM >= -0.51, "动态GM有下界");
        }

        @Test
        @DisplayName("左右舷对称")
        void symmetricalPortStarboard() {
            double gmPositive = MonteCarloEngine.calculateDynamicGM(1.0, 20.0);
            double gmNegative = MonteCarloEngine.calculateDynamicGM(1.0, -20.0);
            assertEquals(gmPositive, gmNegative, 1e-6);
        }
    }

    @Nested
    @DisplayName("参数横摇检测测试")
    class ParametricRollTests {

        @Test
        @DisplayName("波浪周期≈固有周期时检测到参数横摇")
        void nearResonanceDetected() {
            double wavePeriod = 10.0;
            double rollPeriod = 10.5;
            assertTrue(MonteCarloEngine.detectParametricRollRisk(wavePeriod, rollPeriod));
        }

        @Test
        @DisplayName("波浪周期远离固有周期时无参数横摇")
        void farFromResonanceNoRisk() {
            double wavePeriod = 5.0;
            double rollPeriod = 15.0;
            assertFalse(MonteCarloEngine.detectParametricRollRisk(wavePeriod, rollPeriod));
        }

        @Test
        @DisplayName("边界情况：比值=0.8时检测到风险")
        void boundaryLowerDetected() {
            double wavePeriod = 8.0;
            double rollPeriod = 10.0;
            assertTrue(MonteCarloEngine.detectParametricRollRisk(wavePeriod, rollPeriod));
        }

        @Test
        @DisplayName("边界情况：比值=1.2时检测到风险")
        void boundaryUpperDetected() {
            double wavePeriod = 12.0;
            double rollPeriod = 10.0;
            assertTrue(MonteCarloEngine.detectParametricRollRisk(wavePeriod, rollPeriod));
        }

        @Test
        @DisplayName("正好共振（1:1）时检测到风险")
        void exactResonanceDetected() {
            assertTrue(MonteCarloEngine.detectParametricRollRisk(10.0, 10.0));
        }
    }

    @Nested
    @DisplayName("蒙特卡洛模拟测试")
    class SimulationTests {

        @Test
        @DisplayName("小浪高时倾覆概率为0")
        void smallWaveZeroCapsizing() {
            stormParams.waveHeight = 1.0;
            stormParams.windSpeed = 5.0;

            MonteCarloEngine.SimulationResult result =
                    MonteCarloEngine.runSimulation(shipParams, stormParams, 1000);

            assertEquals(0.0, result.capsizingProbability, 1e-6);
            assertTrue(result.maxRollAngle > 0);
            assertTrue(result.maxRollAngle < 30.0, "小浪横摇应<30°");
        }

        @Test
        @DisplayName("倾覆概率在0-1之间")
        void capsizingProbabilityRange() {
            MonteCarloEngine.SimulationResult result =
                    MonteCarloEngine.runSimulation(shipParams, stormParams, 1000);

            assertTrue(result.capsizingProbability >= 0.0);
            assertTrue(result.capsizingProbability <= 1.0);
        }

        @Test
        @DisplayName("最小GM≤基础GM")
        void minGMLessThanBaseGM() {
            MonteCarloEngine.SimulationResult result =
                    MonteCarloEngine.runSimulation(shipParams, stormParams, 1000);

            assertTrue(result.minGM <= shipParams.baseGM + 1e-6);
        }

        @Test
        @DisplayName("迭代次数为正时返回有效结果")
        void positiveIterationsValidResult() {
            MonteCarloEngine.SimulationResult result =
                    MonteCarloEngine.runSimulation(shipParams, stormParams, 500);

            assertNotNull(result);
            assertTrue(result.iterationCount > 0);
        }

        @Test
        @DisplayName("返回时间序列数据")
        void returnsTimeSeries() {
            MonteCarloEngine.SimulationResult result =
                    MonteCarloEngine.runSimulation(shipParams, stormParams, 100);

            assertNotNull(result.rollAngleSeries);
            assertNotNull(result.gmSeries);
            assertFalse(result.rollAngleSeries.isEmpty());
            assertFalse(result.gmSeries.isEmpty());
        }
    }

    @Nested
    @DisplayName("边界与异常测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("0次迭代也能返回结果")
        void zeroIterationsReturnResult() {
            MonteCarloEngine.SimulationResult result =
                    MonteCarloEngine.runSimulation(shipParams, stormParams, 0);
            assertNotNull(result);
        }

        @Test
        @DisplayName("基础GM为负时也能模拟")
        void negativeBaseGMSimulate() {
            shipParams.baseGM = -0.2;
            MonteCarloEngine.SimulationResult result =
                    MonteCarloEngine.runSimulation(shipParams, stormParams, 500);
            assertNotNull(result);
            assertTrue(result.capsizingProbability > 0.5, "负GM船极易倾覆");
        }

        @Test
        @DisplayName("浪高为0时横摇极小")
        void zeroWaveMinimalRoll() {
            stormParams.waveHeight = 0.0;
            stormParams.windSpeed = 0.0;
            MonteCarloEngine.SimulationResult result =
                    MonteCarloEngine.runSimulation(shipParams, stormParams, 500);

            assertTrue(result.maxRollAngle < 10.0, "零浪横摇应很小");
            assertEquals(0.0, result.capsizingProbability, 1e-6);
        }
    }
}
