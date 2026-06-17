package com.sandship.stability.algorithm;

import com.sandship.stability.ship_comparator.ScoringAlgorithm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScoringAlgorithm 评分算法测试")
class ScoringAlgorithmTest {

    @Nested
    @DisplayName("权重计算测试")
    class WeightCalculationTests {

        @Test
        @DisplayName("computeWeights返回值的和应该等于1.0（归一化验证）")
        void computeWeights_shouldSumToOne() {
            List<String> criteria = Arrays.asList("GM", "GZ_MAX", "RANGE");
            Map<String, Double> weights = ScoringAlgorithm.computeWeights(criteria);
            double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(1.0, sum, 1e-9);
        }

        @Test
        @DisplayName("不指定criteria时返回DEFAULT_WEIGHTS的拷贝（不是同一引用）")
        void computeWeights_shouldReturnCopyOfDefaultWeights_whenCriteriaEmpty() {
            Map<String, Double> result = ScoringAlgorithm.computeWeights(Collections.emptyList());
            Map<String, Double> defaults = ScoringAlgorithm.DEFAULT_WEIGHTS;
            assertNotSame(defaults, result);
            assertEquals(defaults.size(), result.size());
            for (Map.Entry<String, Double> entry : defaults.entrySet()) {
                assertEquals(entry.getValue(), result.get(entry.getKey()));
            }
        }

        @Test
        @DisplayName("GM的权重应为0.25（验证权重常量正确性）")
        void getWeight_GM_shouldBe025() {
            assertEquals(0.25, ScoringAlgorithm.getWeight("GM"));
            assertEquals(0.25, ScoringAlgorithm.DEFAULT_WEIGHTS.get("GM"));
        }

        @Test
        @DisplayName("null criteria也返回默认权重拷贝")
        void computeWeights_shouldReturnDefaultCopy_whenCriteriaNull() {
            Map<String, Double> result = ScoringAlgorithm.computeWeights(null);
            assertEquals(ScoringAlgorithm.DEFAULT_WEIGHTS.size(), result.size());
        }
    }

    @Nested
    @DisplayName("归一化测试")
    class NormalizationTests {

        @Test
        @DisplayName("ROLL_PERIOD应做反向归一化（值越小评分越高）")
        void normalize_rollPeriod_shouldBeReversed() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<String> criteria = Collections.singletonList("ROLL_PERIOD");
            Map<UUID, Map<String, BigDecimal>> raw = new LinkedHashMap<>();
            Map<String, BigDecimal> m1 = new HashMap<>();
            m1.put("ROLL_PERIOD", new BigDecimal("10"));
            Map<String, BigDecimal> m2 = new HashMap<>();
            m2.put("ROLL_PERIOD", new BigDecimal("20"));
            raw.put(id1, m1);
            raw.put(id2, m2);

            Map<UUID, Map<String, Double>> normalized = ScoringAlgorithm.normalize(raw, criteria);
            double norm1 = normalized.get(id1).get("NORM_ROLL_PERIOD");
            double norm2 = normalized.get(id2).get("NORM_ROLL_PERIOD");
            assertTrue(norm1 > norm2);
            assertEquals(1.0, norm1, 1e-9);
            assertEquals(0.0, norm2, 1e-9);
        }

        @Test
        @DisplayName("所有值相同时，归一化结果应为0.5")
        void normalize_allValuesSame_shouldReturn05() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            UUID id3 = UUID.randomUUID();
            List<String> criteria = Collections.singletonList("GM");
            Map<UUID, Map<String, BigDecimal>> raw = new LinkedHashMap<>();
            Map<String, BigDecimal> m1 = new HashMap<>();
            m1.put("GM", new BigDecimal("0.8"));
            Map<String, BigDecimal> m2 = new HashMap<>();
            m2.put("GM", new BigDecimal("0.8"));
            Map<String, BigDecimal> m3 = new HashMap<>();
            m3.put("GM", new BigDecimal("0.8"));
            raw.put(id1, m1);
            raw.put(id2, m2);
            raw.put(id3, m3);

            Map<UUID, Map<String, Double>> normalized = ScoringAlgorithm.normalize(raw, criteria);
            for (UUID id : raw.keySet()) {
                assertEquals(0.5, normalized.get(id).get("NORM_GM"), 1e-9);
            }
        }

        @Test
        @DisplayName("空Map应返回空结果")
        void normalize_emptyInput_shouldReturnEmpty() {
            Map<UUID, Map<String, BigDecimal>> empty = Collections.emptyMap();
            List<String> criteria = Arrays.asList("GM", "GZ_MAX");
            Map<UUID, Map<String, Double>> result = ScoringAlgorithm.normalize(empty, criteria);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("普通指标正向归一化（GM值越大评分越高）")
        void normalize_regularMetric_shouldBePositive() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<String> criteria = Collections.singletonList("GM");
            Map<UUID, Map<String, BigDecimal>> raw = new LinkedHashMap<>();
            Map<String, BigDecimal> m1 = new HashMap<>();
            m1.put("GM", new BigDecimal("0.5"));
            Map<String, BigDecimal> m2 = new HashMap<>();
            m2.put("GM", new BigDecimal("1.0"));
            raw.put(id1, m1);
            raw.put(id2, m2);

            Map<UUID, Map<String, Double>> normalized = ScoringAlgorithm.normalize(raw, criteria);
            double norm1 = normalized.get(id1).get("NORM_GM");
            double norm2 = normalized.get(id2).get("NORM_GM");
            assertTrue(norm2 > norm1);
            assertEquals(0.0, norm1, 1e-9);
            assertEquals(1.0, norm2, 1e-9);
        }
    }

    @Nested
    @DisplayName("评分计算测试")
    class ScoreCalculationTests {

        @Test
        @DisplayName("score = Σ(norm × weight)，验证手算与算法一致")
        void score_shouldBeWeightedSum() {
            UUID id = UUID.randomUUID();
            Map<UUID, Map<String, Double>> normalized = new LinkedHashMap<>();
            Map<String, Double> norms = new HashMap<>();
            norms.put("NORM_GM", 0.8);
            norms.put("NORM_GZ_MAX", 0.6);
            norms.put("NORM_RANGE", 0.4);
            normalized.put(id, norms);

            Map<String, Double> weights = new LinkedHashMap<>();
            weights.put("GM", 0.5);
            weights.put("GZ_MAX", 0.3);
            weights.put("RANGE", 0.2);

            Map<UUID, BigDecimal> scores = ScoringAlgorithm.score(normalized, weights);
            double expected = 0.8 * 0.5 + 0.6 * 0.3 + 0.4 * 0.2;
            assertEquals(expected, scores.get(id).doubleValue(), 1e-4);
        }
    }

    @Nested
    @DisplayName("排名测试")
    class RankTests {

        @Test
        @DisplayName("rank应按score降序排列")
        void rank_shouldBeDescending() {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            UUID id3 = UUID.randomUUID();
            Map<UUID, BigDecimal> scores = new LinkedHashMap<>();
            scores.put(id1, new BigDecimal("0.3000"));
            scores.put(id2, new BigDecimal("0.9000"));
            scores.put(id3, new BigDecimal("0.6000"));

            List<Map.Entry<UUID, BigDecimal>> ranked = ScoringAlgorithm.rank(scores);
            assertEquals(3, ranked.size());
            assertEquals(id2, ranked.get(0).getKey());
            assertEquals(id3, ranked.get(1).getKey());
            assertEquals(id1, ranked.get(2).getKey());
        }
    }
}
