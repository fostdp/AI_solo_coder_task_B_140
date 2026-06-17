package com.sandship.stability.vr_loading;

import com.sandship.stability.entity.CargoHold;
import com.sandship.stability.entity.Ship;
import com.sandship.stability.vr_loading.strategy.StrategyEngine;
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
    @DisplayName("calculateDefaultLoadWeight 默认装载重量测试")
    class DefaultLoadWeightTests {

        @Test
        @DisplayName("大舱(500t剩余)→约100t (500×0.2)")
        void calculateDefaultLoadWeight_largeHold_100tons() {
            double result = engine.calculateDefaultLoadWeight(500.0, 1000.0);
            assertEquals(100.0, result, 0.01);
        }

        @Test
        @DisplayName("小舱(10t剩余)→5t (max(5, 10×0.2))")
        void calculateDefaultLoadWeight_smallHold_5tons() {
            double result = engine.calculateDefaultLoadWeight(10.0, 100.0);
            assertEquals(5.0, result, 0.01);
        }

        @Test
        @DisplayName("刚好5t剩余→5t (max(5, 5×0.2)=5)")
        void calculateDefaultLoadWeight_exact5tons_5tons() {
            double result = engine.calculateDefaultLoadWeight(5.0, 100.0);
            assertEquals(5.0, result, 0.01);
        }

        @Test
        @DisplayName("available<remaining (货少)→取available")
        void calculateDefaultLoadWeight_availableLess_returnsAvailable() {
            double result = engine.calculateDefaultLoadWeight(500.0, 50.0);
            assertEquals(50.0, result, 0.01);
        }

        @Test
        @DisplayName("剩余容量≤0时返回0")
        void calculateDefaultLoadWeight_zeroOrNegative_returnsZero() {
            assertEquals(0.0, engine.calculateDefaultLoadWeight(0.0, 100.0));
            assertEquals(0.0, engine.calculateDefaultLoadWeight(-10.0, 100.0));
        }

        @Test
        @DisplayName("available=0时返回0")
        void calculateDefaultLoadWeight_noAvailable_returnsZero() {
            assertEquals(0.0, engine.calculateDefaultLoadWeight(500.0, 0.0));
        }
    }

    @Nested
    @DisplayName("recommendLoading 装载推荐测试")
    class RecommendLoadingTests {

        private Ship createTestShip(double length, double deadweight) {
            Ship ship = new Ship();
            ship.setId(UUID.randomUUID());
            ship.setName("测试船");
            ship.setLengthOverall(BigDecimal.valueOf(length));
            ship.setDeadweightTons(BigDecimal.valueOf(deadweight));
            return ship;
        }

        private CargoHold createHold(int number, double maxWeight, double cgX, double cgZ, boolean isTank) {
            CargoHold hold = new CargoHold();
            hold.setId(UUID.randomUUID());
            hold.setHoldNumber(number);
            hold.setMaxWeight(BigDecimal.valueOf(maxWeight));
            hold.setCapacityCubic(BigDecimal.valueOf(maxWeight * 2));
            hold.setCenterGravityX(BigDecimal.valueOf(cgX));
            hold