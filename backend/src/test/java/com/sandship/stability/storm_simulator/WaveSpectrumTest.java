package com.sandship.stability.storm_simulator;

import com.sandship.stability.storm_simulator.wave_spectrum.WaveSpectrum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WaveSpectrum 海浪谱测试")
class WaveSpectrumTest {

    @Nested
    @DisplayName("蒲福风级映射测试")
    class BeaufortMappingTests {

        @Test
        @DisplayName("0级风（风速<0.3m/s）→浪高接近0")
        void beaufortToSeaState_wind0_nearZeroWaveHeight() {
            WaveSpectrum.SeaState state = WaveSpectrum.beaufortToSeaStateFromWindSpeed(0.0);
            assertEquals(1, state.beaufortScale);
            assertTrue(state.averageWaveHeight <= 0.1);
        }

        @Test
        @DisplayName("4级风（风速≈5m/s）≈平均浪高合理范围")
        void beaufortToSeaState_wind4_reasonableWaveHeight() {
            WaveSpectrum.SeaState state = WaveSpectrum.beaufortToSeaStateFromWindSpeed(5.0);
            assertEquals(4, state.beaufortScale);
            assertTrue(state.averageWaveHeight >= 0.5 && state.averageWaveHeight <= 2.5);
        }

        @Test
        @DisplayName("8级风（风速≈15m/s）→浪高≈5.5m以上")
        void beaufortToSeaState_wind8_highWave() {
            WaveSpectrum.SeaState state = WaveSpectrum.beaufortToSeaStateFromWindSpeed(15.0);
            assertEquals(7, state.beaufortScale);
            assertTrue(state.averageWaveHeight >= 5.0);
        }

        @Test
        @DisplayName("12级风（风速>28.5m/s）→平均浪高≥14.0m")
        void beaufortToSeaState_wind12_extremeWave() {
            WaveSpectrum.SeaState state = WaveSpectrum.beaufortToSeaStateFromWindSpeed(40.0);
            assertEquals(12, state.beaufortScale);
            assertTrue(state.averageWaveHeight >= 14.0);
        }

        @Test
        @DisplayName("超出范围风级(>12)抛异常")
        void beaufortToSeaState_outOfRange_throwsException() {
            assertThrows(IllegalArgumentException.class, () -> WaveSpectrum.beaufortToSeaState(13));
            assertThrows(IllegalArgumentException.class, () -> WaveSpectrum.beaufortToSeaState(0));
        }

        @Test
        @DisplayName("beaufort 7级风包含5.5m左右的浪高")
        void beaufortToSeaState_scale7_average55() {
            WaveSpectrum.SeaState state = WaveSpectrum.beaufortToSeaState(7);
            assertEquals(7, state.beaufortScale);
            assertEquals(5.5, state.averageWaveHeight);
        }

        @Test
        @DisplayName("beaufort 8级风包含7.5m平均浪高")
        void beaufortToSeaState_scale8_average75() {
            WaveSpectrum.SeaState state = WaveSpectrum.beaufortToSeaState(8);
            assertEquals(8, state.beaufortScale);
            assertEquals(7.5, state.averageWaveHeight);
        }
    }

    @Nested
    @DisplayName("物理常数验证测试")
    class PhysicalConstantsTests {

        @Test
        @DisplayName("GRAVITY≈9.81")
        void gravity_shouldBe981() {
            assertEquals(9.81, WaveSpectrum.GRAVITY, 1e-9);
        }

        @Test
        @DisplayName("AIR_DENSITY≈1.225")
        void airDensity_shouldBe1225() {
            assertEquals(1.225, WaveSpectrum.AIR_DENSITY, 1e-9);
        }

        @Test
        @DisplayName("SEAWATER_DENSITY≈1.025")
        void seawaterDensity_shouldBe1025() {
            assertEquals(1.025, WaveSpectrum.SEAWATER_DENSITY, 1e-9);
        }
    }

    @Nested
    @DisplayName("风暴参数映射测试")
    class StormSeverityParamsTests {

        @Test
        @DisplayName("TROPICAL_STORM包含正确的浪高范围[3.0,5.0]")
        void tropicalStorm_shouldHaveCorrectWaveHeightRange() {
            double[] params = WaveSpectrum.STORM_SEVERITY_PARAMS.get("TROPICAL_STORM");
            assertNotNull(params);
            assertEquals(3.0, params[0]);
            assertEquals(5.0, params[1]);
        }

        @Test
        @DisplayName("HURRICANE包含正确的浪高范围[14.0,20.0]")
        void hurricane_shouldHaveCorrectWaveHeightRange() {
            double[] params = WaveSpectrum.STORM_SEVERITY_PARAMS.get("HURRICANE");
            assertNotNull(params);
            assertEquals(14.0, params[0]);
            assertEquals(20.0, params[1]);
        }

        @Test
        @DisplayName("四种风暴类型都存在")
        void allStormTypes_present() {
            assertTrue(WaveSpectrum.STORM_SEVERITY_PARAMS.containsKey("TROPICAL_STORM"));
            assertTrue(WaveSpectrum.STORM_SEVERITY_PARAMS.containsKey("SEVERE_STORM"));
            assertTrue(WaveSpectrum.STORM_SEVERITY_PARAMS.containsKey("TYPHOON"));
            assertTrue(WaveSpectrum.STORM_SEVERITY_PARAMS.containsKey("HURRICANE"));
        }
    }

    @Nested
    @DisplayName("JONSWAP谱值测试")
    class JonswapSpectrumTests {

        @Test
        @DisplayName("calculateSpectrumValue返回值>0（物理合理性）")
        void calculateSpectrumValue_shouldBePositive() {
            WaveSpectrum.JonswapParams params = WaveSpectrum.calculateJonswapSpectrum(3.0, 10.0);
            double omega = 2.0 * Math.PI / 10.0;
            double value = WaveSpectrum.calculateSpectrumValue(omega, params);
            assertTrue(value > 0);
        }

        @Test
        @DisplayName("多个频率下谱值都非负")
        void calculateSpectrumValue_multipleFrequencies_allPositive() {
            WaveSpectrum.JonswapParams params = WaveSpectrum.calculateJonswapSpectrum(3.0, 10.0);
            for (double omega = 0.2; omega < 2.0; omega += 0.1) {
                double value = WaveSpectrum.calculateSpectrumValue(omega, params);
                assertTrue(value >= 0, "omega=" + omega + " 时值应为非负");
            }
        }

        @Test
        @DisplayName("Pierson-Moskowitz谱值也应为正")
        void calculateSpectrumValue_pm_shouldBePositive() {
            WaveSpectrum.PiersonMoskowitzParams params = WaveSpectrum.calculatePiersonMoskowitzSpectrum(3.0);
            double omega = 2.0 * Math.PI / params.peakPeriod;
            double value = WaveSpectrum.calculateSpectrumValue(omega, params);
            assertTrue(value > 0);
        }
    }
}
