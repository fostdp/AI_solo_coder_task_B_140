package com.sandship.stability.ship_comparator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public final class ScoringAlgorithm {

    public static final Map<String, Double> DEFAULT_WEIGHTS = Map.ofEntries(
            Map.entry("GM", 0.25),
            Map.entry("GZ_MAX", 0.20),
            Map.entry("RANGE", 0.15),
            Map.entry("GZ_AREA", 0.15),
            Map.entry("ROLL_PERIOD", 0.10),
            Map.entry("DISPLACEMENT", 0.05),
            Map.entry("DEADWEIGHT", 0.05),
            Map.entry("BOW_HEIGHT", 0.05)
    );

    public static final Set<String> LOWER_IS_BETTER = Set.of("ROLL_PERIOD");

    public static final double DEFAULT_WEIGHT = 0.05;

    private ScoringAlgorithm() {
    }

    public static double getWeight(String metric) {
        return DEFAULT_WEIGHTS.getOrDefault(metric, DEFAULT_WEIGHT);
    }

    public static Map<String, Double> computeWeights(List<String> criteria) {
        if (criteria == null || criteria.isEmpty()) {
            return new HashMap<>(DEFAULT_WEIGHTS);
        }
        double total = 0.0;
        Map<String, Double> result = new LinkedHashMap<>();
        for (String c : criteria) {
            double w = getWeight(c);
            result.put(c, w);
            total += w;
        }
        if (total > 0) {
            result.replaceAll((k, v) -> v / total);
        }
        return result;
    }

    public static Map<UUID, Map<String, Double>> normalize(
            Map<UUID, Map<String, BigDecimal>> rawMetrics, List<String> criteria) {

        Map<UUID, Map<String, Double>> normalized = new LinkedHashMap<>();
        for (UUID id : rawMetrics.keySet()) {
            normalized.put(id, new LinkedHashMap<>());
        }

        for (String metric : criteria) {
            List<BigDecimal> values = new ArrayList<>();
            for (UUID id : rawMetrics.keySet()) {
                Map<String, BigDecimal> m = rawMetrics.get(id);
                if (m != null && m.get(metric) != null) {
                    values.add(m.get(metric));
                }
            }
            if (values.isEmpty()) {
                continue;
            }
            BigDecimal min = Collections.min(values);
            BigDecimal max = Collections.max(values);
            double range = max.subtract(min).doubleValue();

            boolean lowerBetter = LOWER_IS_BETTER.contains(metric);
            for (UUID id : rawMetrics.keySet()) {
                Map<String, BigDecimal> m = rawMetrics.get(id);
                if (m == null || m.get(metric) == null) {
                    normalized.get(id).put("NORM_" + metric, 0.0);
                    continue;
                }
                BigDecimal val = m.get(metric);
                double norm;
                if (range < 1e-9) {
                    norm = 0.5;
                } else {
                    norm = (val.subtract(min).doubleValue()) / range;
                    if (lowerBetter) {
                        norm = 1.0 - norm;
                    }
                }
                normalized.get(id).put("NORM_" + metric, norm);
            }
        }
        return normalized;
    }

    public static Map<UUID, BigDecimal> score(
            Map<UUID, Map<String, Double>> normalized, Map<String, Double> weights) {

        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        for (UUID id : normalized.keySet()) {
            double score = 0.0;
            Map<String, Double> norms = normalized.get(id);
            for (Map.Entry<String, Double> wEntry : weights.entrySet()) {
                String metric = wEntry.getKey();
                double w = wEntry.getValue();
                Double norm = norms.get("NORM_" + metric);
                if (norm != null) {
                    score += norm * w;
                }
            }
            result.put(id, BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP));
        }
        return result;
    }

    public static List<Map.Entry<UUID, BigDecimal>> rank(Map<UUID, BigDecimal> scores) {
        List<Map.Entry<UUID, BigDecimal>> list = new ArrayList<>(scores.entrySet());
        list.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        return list;
    }
}
