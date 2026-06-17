package com.sandship.stability.ship_comparator;

import com.sandship.stability.dto.ShipComparisonRequest;
import com.sandship.stability.dto.ShipComparisonResultDTO;
import com.sandship.stability.dto.ShipDTO;
import com.sandship.stability.entity.SensorData;
import com.sandship.stability.entity.Ship;
import com.sandship.stability.entity.ShipComparison;
import com.sandship.stability.entity.StabilityResult;
import com.sandship.stability.repository.ShipComparisonRepository;
import com.sandship.stability.repository.ShipRepository;
import com.sandship.stability.stability_simulator.StabilitySimulatorService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ShipComparatorService {

    @Autowired
    private StabilitySimulatorService stabilitySimulatorService;

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private ShipComparisonRepository shipComparisonRepository;

    @Autowired
    private Executor stabilityExecutor;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public ShipComparisonResultDTO compareShips(ShipComparisonRequest request) {
        log.info("[ShipComparator] 开始船型对比 - 船舶数量: {}, 工况: {}, 指标: {}",
                request.getShipIds().size(), request.getLoadingCondition(), request.getComparisonCriteria());

        try {
            List<Ship> ships = shipRepository.findAllById(request.getShipIds());
            if (ships.size() != request.getShipIds().size()) {
                throw new IllegalArgumentException("部分船舶不存在");
            }

            List<CompletableFuture<ShipComparisonResultDTO.ShipComparisonItem>> futures = ships.stream()
                    .map(ship -> CompletableFuture.supplyAsync(
                            () -> calculateShipMetrics(ship, request), stabilityExecutor))
                    .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<ShipComparisonResultDTO.ShipComparisonItem> items = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            applyScoring(items, request.getComparisonCriteria());

            List<String> rankingSummary = generateRankingSummary(items, request.getComparisonCriteria());

            ShipComparison comparison = saveComparison(request, items, rankingSummary);

            return convertToDTO(comparison, items, rankingSummary);

        } catch (Exception e) {
            log.error("[ShipComparator] 船型对比失败", e);
            throw new RuntimeException("船型对比失败: " + e.getMessage(), e);
        }
    }

    private ShipComparisonResultDTO.ShipComparisonItem calculateShipMetrics(
            Ship ship, ShipComparisonRequest request) {

        log.debug("[ShipComparator] 计算船舶稳性 - 船舶: {}", ship.getName());

        SensorData sensorData = generateSensorData(ship, request);
        StabilityResult stabilityResult = stabilitySimulatorService.calculateStability(ship.getId(), sensorData);

        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        metrics.put("GM", stabilityResult.getGmValue());
        metrics.put("GZ_MAX", calculateGzMax(stabilityResult));
        metrics.put("GZ_AREA", calculateGzArea(stabilityResult));
        metrics.put("ROLL_PERIOD", stabilityResult.getRollPeriod());
        metrics.put("RANGE", calculateStabilityRange(stabilityResult));
        metrics.put("DISPLACEMENT", stabilityResult.getDisplacementActual());
        metrics.put("DEADWEIGHT", ship.getDeadweightTons());
        metrics.put("BOW_HEIGHT", ship.getBowHeight() != null ? ship.getBowHeight() : BigDecimal.ZERO);

        ShipComparisonResultDTO.ShipComparisonItem item = new ShipComparisonResultDTO.ShipComparisonItem();
        item.setShipId(ship.getId());
        item.setShipName(ship.getName());
        item.setShipType(ship.getShipType());
        item.setShipFamily(ship.getShipFamily());
        item.setCategory(ship.getShipCategory());
        item.setMetrics(metrics);

        return item;
    }

    void applyScoring(List<ShipComparisonResultDTO.ShipComparisonItem> items, List<String> criteria) {
        if (items == null || items.isEmpty()) return;

        Map<UUID, Map<String, BigDecimal>> rawMetrics = new LinkedHashMap<>();
        for (ShipComparisonResultDTO.ShipComparisonItem item : items) {
            rawMetrics.put(item.getShipId(), item.getMetrics());
        }

        Map<String, Double> weights = ScoringAlgorithm.computeWeights(criteria);
        Map<UUID, Map<String, Double>> normalized = ScoringAlgorithm.normalize(rawMetrics, criteria);
        Map<UUID, BigDecimal> scores = ScoringAlgorithm.score(normalized, weights);
        List<Map.Entry<UUID, BigDecimal>> ranked = ScoringAlgorithm.rank(scores);

        Map<UUID, Integer> rankMap = new LinkedHashMap<>();
        for (int i = 0; i < ranked.size(); i++) {
            rankMap.put(ranked.get(i).getKey(), i + 1);
        }

        for (ShipComparisonResultDTO.ShipComparisonItem item : items) {
            Map<String, Double> norms = normalized.get(item.getShipId());
            if (norms != null) {
                for (Map.Entry<String, Double> ne : norms.entrySet()) {
                    item.getMetrics().put(ne.getKey(), BigDecimal.valueOf(ne.getValue()));
                }
            }
            item.setScore(scores.get(item.getShipId()));
            item.setRank(rankMap.get(item.getShipId()));
        }

        items.sort(Comparator.comparingInt(ShipComparisonResultDTO.ShipComparisonItem::getRank));
    }

    private SensorData generateSensorData(Ship ship, ShipComparisonRequest request) {
        SensorData sensorData = new SensorData();
        sensorData.setShipId(ship.getId());
        sensorData.setTimestamp(LocalDateTime.now());
        sensorData.setRollAngle(BigDecimal.ZERO);
        sensorData.setPitchAngle(BigDecimal.ZERO);
        sensorData.setHeelAngle(BigDecimal.ZERO);
        sensorData.setBilgeWaterLevel(BigDecimal.ZERO);
        sensorData.setWaveHeight(request.getReferenceWaveHeight());

        BigDecimal designDraft = ship.getDesignDraft();
        String loadingCondition = request.getLoadingCondition();

        BigDecimal draft;
        switch (loadingCondition == null ? "" : loadingCondition) {
            case "BALLAST":
                draft = designDraft.multiply(new BigDecimal("0.6"));
                break;
            case "HALF_LOAD":
                draft = designDraft.multiply(new BigDecimal("0.8"));
                break;
            case "FULL_LOAD":
            default:
                draft = designDraft;
                break;
        }

        sensorData.setDraftMean(draft);
        sensorData.setDraftForward(draft.multiply(new BigDecimal("1.02")));
        sensorData.setDraftAft(draft.multiply(new BigDecimal("0.98")));

        return sensorData;
    }

    private BigDecimal calculateGzMax(StabilityResult result) {
        List<Map<String, Object>> curvePoints = result.getCurvePoints();
        if (curvePoints == null || curvePoints.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal maxGz = BigDecimal.ZERO;
        for (Map<String, Object> point : curvePoints) {
            BigDecimal gz = new BigDecimal(point.get("rightingArm").toString());
            if (gz.compareTo(maxGz) > 0) {
                maxGz = gz;
            }
        }
        return maxGz.setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateGzArea(StabilityResult result) {
        List<Map<String, Object>> curvePoints = result.getCurvePoints();
        if (curvePoints == null || curvePoints.size() < 2) {
            return BigDecimal.ZERO;
        }
        double area = 0.0;
        for (int i = 1; i < curvePoints.size(); i++) {
            Map<String, Object> prev = curvePoints.get(i - 1);
            Map<String, Object> curr = curvePoints.get(i);
            double anglePrev = ((Number) prev.get("angle")).doubleValue();
            double angleCurr = ((Number) curr.get("angle")).doubleValue();
            double gzPrev = ((Number) prev.get("rightingArm")).doubleValue();
            double gzCurr = ((Number) curr.get("rightingArm")).doubleValue();
            area += (gzPrev + gzCurr) * (angleCurr - anglePrev) / 2.0;
        }
        return BigDecimal.valueOf(area).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateStabilityRange(StabilityResult result) {
        List<Map<String, Object>> curvePoints = result.getCurvePoints();
        if (curvePoints == null || curvePoints.isEmpty()) {
            return BigDecimal.ZERO;
        }
        double range = 0.0;
        for (Map<String, Object> point : curvePoints) {
            double gz = ((Number) point.get("rightingArm")).doubleValue();
            if (gz > 0) {
                range = ((Number) point.get("angle")).doubleValue();
            } else {
                break;
            }
        }
        return BigDecimal.valueOf(range).setScale(2, RoundingMode.HALF_UP);
    }

    private List<String> generateRankingSummary(List<ShipComparisonResultDTO.ShipComparisonItem> items,
                                                List<String> criteria) {
        List<String> summaries = new ArrayList<>();
        summaries.add(String.format("综合排名：共 %d 艘船舶参与对比", items.size()));
        for (ShipComparisonResultDTO.ShipComparisonItem item : items) {
            summaries.add(String.format("第%d名：%s（%s）- 综合评分：%.4f",
                    item.getRank(), item.getShipName(), item.getCategory(),
                    item.getScore().doubleValue()));
        }
        for (String criterion : criteria) {
            ShipComparisonResultDTO.ShipComparisonItem best = findBestByCriterion(items, criterion);
            if (best != null) {
                summaries.add(String.format("%s最优：%s - %.4f",
                        getCriterionDisplayName(criterion), best.getShipName(),
                        best.getMetrics().getOrDefault(criterion, BigDecimal.ZERO).doubleValue()));
            }
        }
        return summaries;
    }

    private ShipComparisonResultDTO.ShipComparisonItem findBestByCriterion(
            List<ShipComparisonResultDTO.ShipComparisonItem> items, String criterion) {
        if (items.isEmpty()) return null;
        boolean lowerIsBetter = ScoringAlgorithm.LOWER_IS_BETTER.contains(criterion);
        ShipComparisonResultDTO.ShipComparisonItem best = items.get(0);
        for (ShipComparisonResultDTO.ShipComparisonItem item : items) {
            BigDecimal current = item.getMetrics().getOrDefault(criterion, BigDecimal.ZERO);
            BigDecimal bestValue = best.getMetrics().getOrDefault(criterion, BigDecimal.ZERO);
            if (lowerIsBetter) {
                if (current.compareTo(bestValue) < 0) best = item;
            } else {
                if (current.compareTo(bestValue) > 0) best = item;
            }
        }
        return best;
    }

    private String getCriterionDisplayName(String criterion) {
        return Map.of(
                "GM", "GM值",
                "GZ_MAX", "最大复原力臂",
                "GZ_AREA", "稳性曲线面积",
                "ROLL_PERIOD", "横摇周期",
                "RANGE", "稳性范围",
                "DISPLACEMENT", "排水量",
                "DEADWEIGHT", "载重吨",
                "BOW_HEIGHT", "船首高度"
        ).getOrDefault(criterion, criterion);
    }

    private ShipComparison saveComparison(ShipComparisonRequest request,
                                           List<ShipComparisonResultDTO.ShipComparisonItem> items,
                                           List<String> rankingSummary) {
        ShipComparison comparison = new ShipComparison();
        comparison.setComparisonName(request.getComparisonName());
        comparison.setShipIds(request.getShipIds());
        comparison.setComparisonCriteria(request.getComparisonCriteria());
        comparison.setLoadingCondition(request.getLoadingCondition());
        comparison.setReferenceWaveHeight(request.getReferenceWaveHeight());
        comparison.setCreatedBy(request.getCreatedBy());
        comparison.setRankingSummary(String.join("\n", rankingSummary));
        Map<String, Object> results = new LinkedHashMap<>();
        results.put("items", items);
        comparison.setResults(results);
        return shipComparisonRepository.save(comparison);
    }

    public List<ShipComparisonResultDTO> getComparisonHistory(int limit) {
        log.info("[ShipComparator] 获取对比历史 - 数量: {}", limit);
        List<ShipComparison> comparisons;
        if (limit > 0) {
            comparisons = shipComparisonRepository.findTop10ByOrderByCreatedAtDesc();
            if (limit < 10) {
                comparisons = comparisons.subList(0, Math.min(limit, comparisons.size()));
            }
        } else {
            comparisons = shipComparisonRepository.findAll();
            comparisons.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        }
        return comparisons.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    public ShipComparisonResultDTO getComparisonById(UUID id) {
        Optional<ShipComparison> comparisonOpt = shipComparisonRepository.findById(id);
        if (comparisonOpt.isEmpty()) {
            throw new IllegalArgumentException("对比记录不存在: " + id);
        }
        return convertToDTO(comparisonOpt.get());
    }

    @Transactional
    public void deleteComparison(UUID id) {
        if (!shipComparisonRepository.existsById(id)) {
            throw new IllegalArgumentException("对比记录不存在: " + id);
        }
        shipComparisonRepository.deleteById(id);
    }

    public List<ShipDTO> getAvailableShips() {
        log.info("[ShipComparator] 获取可对比船舶列表");
        String sql = "SELECT id, name, ship_type, ship_category, ship_family, ship_variant, " +
                     "length_overall, breadth_molded, depth_molded, design_draft, " +
                     "displacement, deadweight_tons, metacentric_height_design, " +
                     "bow_height, historical_period " +
                     "FROM ships ORDER BY ship_category, ship_family, name";
        Query query = entityManager.createNativeQuery(sql);
        List<Object[]> results = query.getResultList();
        List<ShipDTO> ships = new ArrayList<>();
        for (Object[] row : results) {
            ShipDTO dto = new ShipDTO();
            dto.setId(toUuid(row[0]));
            dto.setName(toStringOrNull(row[1]));
            dto.setShipType(toStringOrNull(row[2]));
            dto.setShipCategory(toStringOrNull(row[3]));
            dto.setShipFamily(toStringOrNull(row[4]));
            dto.setShipVariant(toStringOrNull(row[5]));
            dto.setLengthOverall(toDecimalOrNull(row[6]));
            dto.setBreadthMolded(toDecimalOrNull(row[7]));
            dto.setDepthMolded(toDecimalOrNull(row[8]));
            dto.setDesignDraft(toDecimalOrNull(row[9]));
            dto.setDisplacement(toDecimalOrNull(row[10]));
            dto.setDeadweightTons(toDecimalOrNull(row[11]));
            dto.setMetacentricHeightDesign(toDecimalOrNull(row[12]));
            dto.setBowHeight(toDecimalOrNull(row[13]));
            dto.setHistoricalPeriod(toStringOrNull(row[14]));
            ships.add(dto);
        }
        return ships;
    }

    private UUID toUuid(Object o) {
        return o == null ? null : UUID.fromString(o.toString());
    }

    private String toStringOrNull(Object o) {
        return o == null ? null : o.toString();
    }

    private BigDecimal toDecimalOrNull(Object o) {
        return o == null ? null : new BigDecimal(o.toString());
    }

    @SuppressWarnings("unchecked")
    private ShipComparisonResultDTO convertToDTO(ShipComparison comparison) {
        return convertToDTO(comparison, null, null);
    }

    @SuppressWarnings("unchecked")
    private ShipComparisonResultDTO convertToDTO(ShipComparison comparison,
                                                  List<ShipComparisonResultDTO.ShipComparisonItem> items,
                                                  List<String> rankingSummaryList) {
        ShipComparisonResultDTO dto = new ShipComparisonResultDTO();
        dto.setId(comparison.getId());
        dto.setComparisonName(comparison.getComparisonName());
        dto.setShipIds(comparison.getShipIds());
        dto.setComparisonCriteria(comparison.getComparisonCriteria());
        dto.setLoadingCondition(comparison.getLoadingCondition());
        dto.setReferenceWaveHeight(comparison.getReferenceWaveHeight());
        dto.setCreatedBy(comparison.getCreatedBy());
        dto.setCreatedAt(comparison.getCreatedAt());
        dto.setRankingSummary(comparison.getRankingSummary());

        if (items != null) {
            dto.setComparisonItems(items);
        } else if (comparison.getResults() != null && comparison.getResults().get("items") != null) {
            dto.setComparisonItems(
                    (List<ShipComparisonResultDTO.ShipComparisonItem>) comparison.getResults().get("items"));
        }
        if (rankingSummaryList != null) {
            dto.setRankingSummaryList(rankingSummaryList);
        } else if (comparison.getRankingSummary() != null) {
            dto.setRankingSummaryList(Arrays.asList(comparison.getRankingSummary().split("\n")));
        }
        return dto;
    }
}
