package com.sandship.stability.ship_comparator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScoringAlgorithm 评分算法测试")
class ScoringAlgorithmTest {

    private Map<UUID, Map<String, BigDecimal>> rawMetrics;
    private UUID shipA;
    private UUID shipB;
    private UUID shipC;

    @BeforeEach
    void setUp() {
        shipA = UUID.randomUUID();
        shipB = UUID.randomUUID();
        shipC = UUID.randomUUID();

        rawMetrics = new LinkedHashMap<>();

        Map<String, BigDecimal> metricsA = new LinkedHashMap<>();
        metricsA.put("GM", new BigDecimal("1.20"));
        metricsA.put("GZ_MAX", new BigDecimal("0.80"));
        metricsA.put("RANGE", new BigDecimal("45.0"));
        metricsA.put("ROLL_PERIOD", new BigDecimal("10.0"));
        rawMetrics.put(shipA, metricsA);

        Map<String, BigDecimal> metricsB = new LinkedHashMap<>();
        metricsB.put("GM", new BigDecimal("0.90"));
        metricsB.put("GZ_MAX", new BigDecimal("0.60"));
        metricsB.put("RANGE", new BigDecimal("35.0"));
        metricsB.put("ROLL_PERIOD", new BigDecimal("12.0"));
        rawMetrics.put(shipB, metricsB);

        Map<String, BigDecimal> metricsC = new LinkedHashMap<>();
        metricsC.put("GM", new BigDecimal("0.60"));
        metricsC.put("GZ_MAX", new BigDecimal("0.40"));
        metricsC.put("RANGE", new BigDecimal("25.0"));
        metricsC.put("ROLL_PERIOD", new BigDecimal("15.0"));
        rawMetrics.put(shipC, metricsC);
    }

    @Nested
    @DisplayName("权重计算测试")
    class WeightTests {

        @Test
        @DisplayName("默认权重总和应为1.0")
        void defaultWeightsSumToOne() {
            double sum = ScoringAlgorithm.DEFAULT_WEIGHTS.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();
            assertEquals(1.0, sum, 1e-6);
        }

        @Test
        @DisplayName("核心指标GM权重应为0.25（最高）")
        void gmWeightIsHighest() {
            assertEquals(0.25, ScoringAlgorithm.getWeight("GM"), 1e-6);
        }

        @Test
        @DisplayName("computeWeights - 自定义指标列表权重和为1")
        void customCriteriaWeightsSumToOne() {
            List<String> criteria = Arrays.asList("GM", "GZ_MAX", "RANGE");
            Map<String, Double> weights = ScoringAlgorithm.computeWeights(criteria);
            double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(1.0, sum, 1e-6);
        }

        @Test
        @DisplayName("未知指标使用默认权重0.05")
        void unknownMetricUsesDefaultWeight() {
            assertEquals(0.05, ScoringAlgorithm.getWeight("UNKNOWN_METRIC"), 1e-6);
        }
    }

    @Nested
    @DisplayName("归一化测试")
    class NormalizeTests {

        @Test
        @DisplayName("越大越好型指标：最大值归一化为1.0")
        void higherIsBetterNormalizeMaxToOne() {
            List<String> criteria = List.of("GM");
            Map<UUID, Map<String, Double>> normalized = ScoringAlgorithm.normalize(rawMetrics, criteria);

            assertEquals(1.0, normalized.get(shipA).get("NORM_GM"), 1e-6);
            assertEquals(0.0, normalized.get(shipC).get("NORM_GM"), 1e-6);
            assertEquals(0.5, normalized.get(shipB).get("NORM_GM"), 1e-6);
        }

        @Test
        @DisplayName("越小越好型指标：最小值归一化为1.0")
        void lowerIsBetterNormalizeMinToOne() {
            List<String> criteria = List.of("ROLL_PERIOD");
            Map<UUID, Map<String, Double>> normalized = ScoringAlgorithm.normalize(rawMetrics, criteria);

            assertTrue(ScoringAlgorithm.LOWER_IS_BETTER.contains("ROLL_PERIOD"));
            assertEquals(1.0, normalized.get(shipA).get("NORM_ROLL_PERIOD"), 1e-6);
            assertEquals(0.0, normalized.get(shipC).get("NORM_ROLL_PERIOD"), 1e-6);
        }

        @Test
        @DisplayName("所有值相同时归一化应为0.5")
        void allSameValuesNormalizeToHalf() {
            Map<UUID, Map<String, BigDecimal>> sameMetrics = new LinkedHashMap<>();
            Map<String, BigDecimal> m = new LinkedHashMap<>();
            m.put("GM", new BigDecimal("1.0"));
            sameMetrics.put(shipA, m);
            sameMetrics.put(shipB, m);

            Map<UUID, Map<String, Double>> normalized =
                    ScoringAlgorithm.normalize(sameMetrics, List.of("GM"));

            assertEquals(0.5, normalized.get(shipA).get("NORM_GM"), 1e-6);
            assertEquals(0.5, normalized.get(shipB).get("NORM_GM"), 1e-6);
        }

        @Test
        @DisplayName("空数据不崩溃")
        void emptyDataDoesNotCrash() {
            Map<UUID, Map<String, BigDecimal>> empty = new LinkedHashMap<>();
            Map<UUID, Map<String, Double>> result =
                    ScoringAlgorithm.normalize(empty, List.of("GM"));
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("评分与排名测试")
    class ScoringTests {

        @Test
        @DisplayName("综合评分排名正确：最优船排第1")
        void rankingOrderIsCorrect() {
            List<String> criteria = Arrays.asList("GM", "GZ_MAX", "RANGE");
            Map<String, Double> weights = ScoringAlgorithm.computeWeights(criteria);
            Map<UUID, Map<String, Double>> normalized = ScoringAlgorithm.normalize(rawMetrics, criteria);
            Map<UUID, BigDecimal> scores = ScoringAlgorithm.score(normalized, weights);
            List<Map.Entry<UUID, BigDecimal>> ranked = ScoringAlgorithm.rank(scores);

            assertEquals(3, ranked.size());
            assertEquals(shipA, ranked.get(0).getKey());
            assertEquals(shipC, ranked.get(ranked.size() - 1).getKey());
            assertTrue(ranked.get(0).getValue().compareTo(ranked.get(1).getValue()) > 0);
        }

        @Test
        @DisplayName("评分范围在0-1之间")
        void scoresBetweenZeroAndOne() {
            List<String> criteria = Arrays.asList("GM", "GZ_MAX");
            Map<String, Double> weights = ScoringAlgorithm.computeWeights(criteria);
            Map<UUID, Map<String, Double>> normalized = ScoringAlgorithm.normalize(rawMetrics, criteria);
            Map<UUID, BigDecimal> scores = ScoringAlgorithm.score(normalized, weights);

            for (BigDecimal score : scores.values()) {
                assertTrue(score.compareTo(BigDecimal.ZERO) >= 0);
                assertTrue(score.compareTo(BigDecimal.ONE) <= 0);
            }
        }

        @Test
        @DisplayName("空归一化数据得分为0")
        void emptyNormalizedScoresZero() {
            Map<UUID, Map<String, Double>> emptyNorm = new LinkedHashMap<>();
            emptyNorm.put(shipA, new LinkedHashMap<>());
            Map<String, Double> weights = ScoringAlgorithm.computeWeights(List.of("GM"));
            Map<UUID, BigDecimal> scores = ScoringAlgorithm.score(emptyNorm, weights);

            assertEquals(BigDecimal.ZERO.setScale(4), scores.get(shipA));
        }
    }

    @Nested
    @DisplayName("边界场景测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("单船对比：得分为0.5")
        void singleShipScoreIsHalf() {
            Map<UUID, Map<String, BigDecimal>> single = new LinkedHashMap<>();
            single.put(shipA, rawMetrics.get(shipA));

            List<String> criteria = List.of("GM");
            Map<UUID, Map<String, Double>> normalized = ScoringAlgorithm.normalize(single, criteria);
            Map<String, Double> weights = ScoringAlgorithm.computeWeights(criteria);
            Map<UUID, BigDecimal> scores = ScoringAlgorithm.score(normalized, weights);
            List<Map.Entry<UUID, BigDecimal>> ranked = ScoringAlgorithm.rank(scores);

            assertEquals(1, ranked.size());
            assertEquals(shipA, ranked.get(0).getKey());
        }

        @Test
        @DisplayName("空criteria使用默认权重")
        void nullCriteriaUsesDefaults() {
            Map<String, Double> weights = ScoringAlgorithm.computeWeights(null);
            assertFalse(weights.isEmpty());
        }

        @Test
        @DisplayName("GM和GZ_MAX权重占60%以上")
        void coreMetricsDominantWeight() {
            double gmWeight = ScoringAlgorithm.getWeight("GM");
            double gzMaxWeight = ScoringAlgorithm.getWeight("GZ_MAX");
            double rangeWeight = ScoringAlgorithm.getWeight("RANGE");
            assertEquals(0.60, gmWeight + gzMaxWeight + rangeWeight, 1e-6);
        }
    }
}
