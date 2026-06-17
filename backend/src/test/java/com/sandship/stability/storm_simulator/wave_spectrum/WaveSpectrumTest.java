package com.sandship.stability.storm_simulator.wave_spectrum;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WaveSpectrum 波浪谱测试")
class WaveSpectrumTest {

    @Nested
    @DisplayName("蒲福风级→海况映射测试")
    class BeaufortMappingTests {

        @Test
        @DisplayName("蒲福1级（轻风）对应低海况")
        void beaufort1LowSeaState() {
            WaveSpectrum.SeaState sea = WaveSpectrum.beaufortToSeaState(1);
            assertTrue(sea.significantWaveHeight < 0.5);
            assertEquals(1, sea.beaufortScale);
        }

        @Test
        @DisplayName("蒲福8级（大风）对应中等海况")
        void beaufort8ModerateSeaState() {
            WaveSpectrum.SeaState sea = WaveSpectrum.beaufortToSeaState(8);
            assertTrue(sea.significantWaveHeight > 4.0 && sea.significantWaveHeight < 8.0);
            assertEquals(8, sea.beaufortScale);
        }

        @Test
        @DisplayName("蒲福12级（飓风）对应极端海况")
        void beaufort12ExtremeSeaState() {
            WaveSpectrum.SeaState sea = WaveSpectrum.beaufortToSeaState(12);
            assertTrue(sea.significantWaveHeight > 14.0);
            assertEquals(12, sea.beaufortScale);
        }

        @Test
        @DisplayName("蒲福风级从风速推算（5m/s→约3级）")
        void beaufortFromWindSpeed() {
            WaveSpectrum.SeaState sea = WaveSpectrum.beaufortToSeaStateFromWindSpeed(5.0);
            assertTrue(sea.beaufortScale >= 2 && sea.beaufortScale <= 4);
        }

        @Test
        @DisplayName("风速0m/s返回0级风")
        void zeroWindReturnsBeaufort0() {
            WaveSpectrum.SeaState sea = WaveSpectrum.beaufortToSeaStateFromWindSpeed(0.0);
            assertEquals(0, sea.beaufortScale);
        }
    }

    @Nested
    @DisplayName("JONSWAP谱测试")
    class JonswapTests {

        @Test
        @DisplayName("JONSWAP谱参数包含所有字段")
        void jonswapParamsComplete() {
            WaveSpectrum.JonswapParams params =
                    WaveSpectrum.calculateJonswapSpectrum(3.0, 8.0);

            assertTrue(params.peakFrequency > 0);
            assertTrue(params.alpha > 0 && params.alpha < 1.0);
            assertTrue(params.gamma >= 1.0 && params.gamma <= 7.0);
            assertTrue(params.sigmaA > 0 && params.sigmaB > 0);
        }

        @Test
        @DisplayName("显著波高越大，α值越大")
        void jonswapIncreasesWithWaveHeight() {
            WaveSpectrum.JonswapParams small =
                    WaveSpectrum.calculateJonswapSpectrum(1.0, 8.0);
            WaveSpectrum.JonswapParams large =
                    WaveSpectrum.calculateJonswapSpectrum(5.0, 8.0);

            assertTrue(large.alpha > small.alpha);
        }

        @Test
        @DisplayName("谱值在峰频处最大")
        void jonswapPeaksAtPeakFrequency() {
            WaveSpectrum.JonswapParams params =
                    WaveSpectrum.calculateJonswapSpectrum(3.0, 8.0);

            double peakOmega = params.peakFrequency;
            double peakValue = WaveSpectrum.calculateSpectrumValue(peakOmega, params);
            double leftValue = WaveSpectrum.calculateSpectrumValue(peakOmega * 0.8, params);
            double rightValue = WaveSpectrum.calculateSpectrumValue(peakOmega * 1.2, params);

            assertTrue(peakValue > leftValue);
            assertTrue(peakValue > rightValue);
        }
    }

    @Nested
    @DisplayName("Pierson-Moskowitz谱测试")
    class PiersonMoskowitzTests {

        @Test
        @DisplayName("PM谱参数包含峰频和α")
        void pmParamsComplete() {
            WaveSpectrum.PiersonMoskowitzParams params =
                    WaveSpectrum.calculatePiersonMoskowitzSpectrum(3.0);

            assertTrue(params.peakFrequency > 0);
            assertTrue(params.alpha > 0 && params.alpha < 1.0);
        }

        @Test
        @DisplayName("PM谱峰频与PM公式一致")
        void pmPeakFrequencyFormula() {
            double Hs = 3.0;
            WaveSpectrum.PiersonMoskowitzParams params =
                    WaveSpectrum.calculatePiersonMoskowitzSpectrum(Hs);

            double expectedOmegaP = 2.0 * Math.PI / (10.0 * Math.sqrt(Hs));
            assertEquals(expectedOmegaP, params.peakFrequency, 2.0);
        }

        @Test
        @DisplayName("谱值非负")
        void pmSpectrumNonNegative() {
            WaveSpectrum.PiersonMoskowitzParams params =
                    WaveSpectrum.calculatePiersonMoskowitzSpectrum(3.0);

            for (double omega = 0.1; omega < 5.0; omega += 0.1) {
                double value = WaveSpectrum.calculateSpectrumValue(omega, params);
                assertTrue(value >= 0, "谱值不能为负: " + omega);
            }
        }
    }

    @Nested
    @DisplayName("物理常数测试")
    class ConstantsTests {

        @Test
        @DisplayName("空气密度=1.225 kg/m³")
        void airDensity() {
            assertEquals(1.225, WaveSpectrum.AIR_DENSITY, 1e-6);
        }

        @Test
        @DisplayName("海水密度=1.025 t/m³")
        void seawaterDensity() {
            assertEquals(1.025, WaveSpectrum.SEAWATER_DENSITY, 1e-6);
        }

        @Test
        @DisplayName("重力加速度=9.81 m/s²")
        void gravity() {
            assertEquals(9.81, WaveSpectrum.GRAVITY, 1e-6);
        }
    }

    @Nested
    @DisplayName("风暴强度参数测试")
    class StormSeverityTests {

        @Test
        @DisplayName("TROPICAL_STORM级浪高范围合理")
        void tropicalStormWaveHeightRange() {
            double[] params = WaveSpectrum.STORM_SEVERITY_PARAMS.get("TROPICAL_STORM");
            assertNotNull(params);
            assertEquals(6, params.length);
            assertTrue(params[0] > 0);
        }

        @Test
        @DisplayName("HURRICANE级浪高最大")
        void hurricaneHasHighestWaves() {
            double[] tropical = WaveSpectrum.STORM_SEVERITY_PARAMS.get("TROPICAL_STORM");
            double[] hurricane = WaveSpectrum.STORM_SEVERITY_PARAMS.get("HURRICANE");
            assertNotNull(tropical);
            assertNotNull(hurricane);
            assertTrue(hurricane[0] > tropical[0]);
            assertTrue(hurricane[1] > tropical[1]);
        }

        @Test
        @DisplayName("4个风暴等级都有定义")
        void allFourStormLevelsDefined() {
            assertEquals(4, WaveSpectrum.STORM_SEVERITY_PARAMS.size());
            assertTrue(WaveSpectrum.STORM_SEVERITY_PARAMS.containsKey("TROPICAL_STORM"));
            assertTrue(WaveSpectrum.STORM_SEVERITY_PARAMS.containsKey("SEVERE_STORM"));
            assertTrue(WaveSpectrum.STORM_SEVERITY_PARAMS.containsKey("TYPHOON"));
            assertTrue(WaveSpectrum.STORM_SEVERITY_PARAMS.containsKey("HURRICANE"));
        }
    }
}
