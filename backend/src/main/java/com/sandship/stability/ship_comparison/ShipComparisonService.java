package com.sandship.stability.ship_comparison;

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
import java.util.stream.Collectors;

@Slf4j
@Service
public class ShipComparisonService {

    private static final Map<String, BigDecimal> CRITERIA_WEIGHTS = new HashMap<>();

    static {
        CRITERIA_WEIGHTS.put("GM", new BigDecimal("0.25"));
        CRITERIA_WEIGHTS.put("GZ_MAX", new BigDecimal("0.20"));
        CRITERIA_WEIGHTS.put("GZ_AREA", new BigDecimal("0.15"));
        CRITERIA_WEIGHTS.put("ROLL_PERIOD", new BigDecimal("0.10"));
        CRITERIA_WEIGHTS.put("RANGE", new BigDecimal("0.15"));
        CRITERIA_WEIGHTS.put("DISPLACEMENT", new BigDecimal("0.05"));
        CRITERIA_WEIGHTS.put("DEADWEIGHT", new BigDecimal("0.05"));
        CRITERIA_WEIGHTS.put("BOW_HEIGHT", new BigDecimal("0.05"));
    }

    @Autowired
    private StabilitySimulatorService stabilitySimulatorService;

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private ShipComparisonRepository shipComparisonRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public ShipComparisonResultDTO compareShips(ShipComparisonRequest request) {
        log.info("[ShipComparison] 开始船型对比 - 船舶数量: {}, 工况: {}, 指标: {}",
                request.getShipIds().size(), request.getLoadingCondition(), request.getComparisonCriteria());

        try {
            List<Ship> ships = shipRepository.findAllById(request.getShipIds());
            if (ships.size() != request.getShipIds().size()) {
                throw new IllegalArgumentException("部分船舶不存在");
            }

            List<CompletableFuture<ShipComparisonResultDTO.ShipComparisonItem>> futures = ships.stream()
                    .map(ship -> CompletableFuture.supplyAsync(() ->
                            calculateShipMetrics(ship, request)))
                    .collect(Collectors.toList());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<ShipComparisonResultDTO.ShipComparisonItem> items = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());

            calculateScoresAndRank(items, request.getComparisonCriteria());

            List<String> rankingSummary = generateRankingSummary(items, request.getComparisonCriteria());

            ShipComparison comparison = saveComparison(request, items, rankingSummary);

            return convertToDTO(comparison, items, rankingSummary);

        } catch (Exception e) {
            log.error("[ShipComparison] 船型对比失败", e);
            throw new RuntimeException("船型对比失败: " + e.getMessage(), e);
        }
    }

    private ShipComparisonResultDTO.ShipComparisonItem calculateShipMetrics(
            Ship ship, ShipComparisonRequest request) {

        log.info("[ShipComparison] 计算船舶稳性 - 船舶: {}", ship.getName());

        SensorData sensorData = generateSensorData(ship, request);
        StabilityResult stabilityResult = stabilitySimulatorService.calculateStability(ship.getId(), sensorData);

        Map<String, BigDecimal> metrics = new LinkedHashMap<>();

        BigDecimal gm = stabilityResult.getGmValue();
        metrics.put("GM", gm);

        BigDecimal gzMax = calculateGzMax(stabilityResult);
        metrics.put("GZ_MAX", gzMax);

        BigDecimal gzArea = calculateGzArea(stabilityResult);
        metrics.put("GZ_AREA", gzArea);

        metrics.put("ROLL_PERIOD", stabilityResult.getRollPeriod());

        BigDecimal range = calculateStabilityRange(stabilityResult);
        metrics.put("RANGE", range);

        metrics.put("DISPLACEMENT", stabilityResult.getDisplacementActual());
        metrics.put("DEADWEIGHT", ship.getDeadweightTons());

        BigDecimal bowHeight = getShipBowHeight(ship.getId());
        metrics.put("BOW_HEIGHT", bowHeight);

        ShipComparisonResultDTO.ShipComparisonItem item = new ShipComparisonResultDTO.ShipComparisonItem();
        item.setShipId(ship.getId());
        item.setShipName(ship.getName());
        item.setShipType(ship.getShipType());
        item.setShipFamily(getShipFamily(ship.getId()));
        item.setCategory(getShipCategory(ship.getId()));
        item.setMetrics(metrics);

        return item;
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
        switch (loadingCondition) {
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

    private void calculateScoresAndRank(List<ShipComparisonResultDTO.ShipComparisonItem> items,
                                        List<String> criteria) {
        if (items.isEmpty()) return;

        for (String criterion : criteria) {
            normalizeMetrics(items, criterion);
        }

        for (ShipComparisonResultDTO.ShipComparisonItem item : items) {
            BigDecimal totalScore = BigDecimal.ZERO;
            Map<String, BigDecimal> metrics = item.getMetrics();

            for (String criterion : criteria) {
                BigDecimal normalizedValue = metrics.getOrDefault("NORM_" + criterion, BigDecimal.ZERO);
                BigDecimal weight = CRITERIA_WEIGHTS.getOrDefault(criterion, new BigDecimal("0.1"));
                totalScore = totalScore.add(normalizedValue.multiply(weight));
            }

            item.setScore(totalScore.setScale(4, RoundingMode.HALF_UP));
        }

        items.sort((a, b) -> b.getScore().compareTo(a.getScore()));

        for (int i = 0; i < items.size(); i++) {
            items.get(i).setRank(i + 1);
        }
    }

    private void normalizeMetrics(List<ShipComparisonResultDTO.ShipComparisonItem> items, String criterion) {
        if (items.isEmpty()) return;

        BigDecimal min = items.stream()
                .map(item -> item.getMetrics().getOrDefault(criterion, BigDecimal.ZERO))
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal max = items.stream()
                .map(item -> item.getMetrics().getOrDefault(criterion, BigDecimal.ZERO))
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        boolean lowerIsBetter = "ROLL_PERIOD".equals(criterion);

        BigDecimal range = max.subtract(min);
        if (range.compareTo(BigDecimal.ZERO) == 0) {
            for (ShipComparisonResultDTO.ShipComparisonItem item : items) {
                item.getMetrics().put("NORM_" + criterion, new BigDecimal("0.5"));
            }
            return;
        }

        for (ShipComparisonResultDTO.ShipComparisonItem item : items) {
            BigDecimal value = item.getMetrics().getOrDefault(criterion, BigDecimal.ZERO);
            BigDecimal normalized;

            if (lowerIsBetter) {
                normalized = BigDecimal.ONE.subtract(
                        value.subtract(min).divide(range, 4, RoundingMode.HALF_UP));
            } else {
                normalized = value.subtract(min).divide(range, 4, RoundingMode.HALF_UP);
            }

            item.getMetrics().put("NORM_" + criterion, normalized);
        }
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
                String criterionName = getCriterionDisplayName(criterion);
                summaries.add(String.format("%s最优：%s - %.4f",
                        criterionName, best.getShipName(),
                        best.getMetrics().getOrDefault(criterion, BigDecimal.ZERO).doubleValue()));
            }
        }

        return summaries;
    }

    private ShipComparisonResultDTO.ShipComparisonItem findBestByCriterion(
            List<ShipComparisonResultDTO.ShipComparisonItem> items, String criterion) {
        if (items.isEmpty()) return null;

        boolean lowerIsBetter = "ROLL_PERIOD".equals(criterion);
        ShipComparisonResultDTO.ShipComparisonItem best = items.get(0);

        for (ShipComparisonResultDTO.ShipComparisonItem item : items) {
            BigDecimal current = item.getMetrics().getOrDefault(criterion, BigDecimal.ZERO);
            BigDecimal bestValue = best.getMetrics().getOrDefault(criterion, BigDecimal.ZERO);

            if (lowerIsBetter) {
                if (current.compareTo(bestValue) < 0) {
                    best = item;
                }
            } else {
                if (current.compareTo(bestValue) > 0) {
                    best = item;
                }
            }
        }

        return best;
    }

    private String getCriterionDisplayName(String criterion) {
        Map<String, String> displayNames = new HashMap<>();
        displayNames.put("GM", "GM值");
        displayNames.put("GZ_MAX", "最大复原力臂");
        displayNames.put("GZ_AREA", "稳性曲线面积");
        displayNames.put("ROLL_PERIOD", "横摇周期");
        displayNames.put("RANGE", "稳性范围");
        displayNames.put("DISPLACEMENT", "排水量");
        displayNames.put("DEADWEIGHT", "载重吨");
        displayNames.put("BOW_HEIGHT", "船首高度");
        return displayNames.getOrDefault(criterion, criterion);
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
        log.info("[ShipComparison] 获取对比历史 - 数量: {}", limit);

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

        return comparisons.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public ShipComparisonResultDTO getComparisonById(UUID id) {
        log.info("[ShipComparison] 获取对比详情 - ID: {}", id);

        Optional<ShipComparison> comparisonOpt = shipComparisonRepository.findById(id);
        if (comparisonOpt.isEmpty()) {
            throw new IllegalArgumentException("对比记录不存在: " + id);
        }

        return convertToDTO(comparisonOpt.get());
    }

    @Transactional
    public void deleteComparison(UUID id) {
        log.info("[ShipComparison] 删除对比记录 - ID: {}", id);

        if (!shipComparisonRepository.existsById(id)) {
            throw new IllegalArgumentException("对比记录不存在: " + id);
        }

        shipComparisonRepository.deleteById(id);
    }

    public List<ShipDTO> getAvailableShips() {
        log.info("[ShipComparison] 获取可对比船舶列表");

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
            dto.setId(UUID.fromString(row[0].toString()));
            dto.setName(row[1] != null ? row[1].toString() : null);
            dto.setShipType(row[2] != null ? row[2].toString() : null);
            dto.setShipCategory(row[3] != null ? row[3].toString() : null);
            dto.setShipFamily(row[4] != null ? row[4].toString() : null);
            dto.setShipVariant(row[5] != null ? row[5].toString() : null);
            dto.setLengthOverall(row[6] != null ? new BigDecimal(row[6].toString()) : null);
            dto.setBreadthMolded(row[7] != null ? new BigDecimal(row[7].toString()) : null);
            dto.setDepthMolded(row[8] != null ? new BigDecimal(row[8].toString()) : null);
            dto.setDesignDraft(row[9] != null ? new BigDecimal(row[9].toString()) : null);
            dto.setDisplacement(row[10] != null ? new BigDecimal(row[10].toString()) : null);
            dto.setDeadweightTons(row[11] != null ? new BigDecimal(row[11].toString()) : null);
            dto.setMetacentricHeightDesign(row[12] != null ? new BigDecimal(row[12].toString()) : null);
            dto.setBowHeight(row[13] != null ? new BigDecimal(row[13].toString()) : null);
            dto.setHistoricalPeriod(row[14] != null ? row[14].toString() : null);
            ships.add(dto);
        }

        return ships;
    }

    private String getShipCategory(UUID shipId) {
        String sql = "SELECT ship_category FROM ships WHERE id = :id";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("id", shipId);
        try {
            Object result = query.getSingleResult();
            return result != null ? result.toString() : "ANCIENT";
        } catch (Exception e) {
            return "ANCIENT";
        }
    }

    private String getShipFamily(UUID shipId) {
        String sql = "SELECT ship_family FROM ships WHERE id = :id";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("id", shipId);
        try {
            Object result = query.getSingleResult();
            return result != null ? result.toString() : "沙船";
        } catch (Exception e) {
            return "沙船";
        }
    }

    private BigDecimal getShipBowHeight(UUID shipId) {
        String sql = "SELECT bow_height FROM ships WHERE id = :id";
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("id", shipId);
        try {
            Object result = query.getSingleResult();
            return result != null ? new BigDecimal(result.toString()) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

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
            List<ShipComparisonResultDTO.ShipComparisonItem> storedItems =
                    (List<ShipComparisonResultDTO.ShipComparisonItem>) comparison.getResults().get("items");
            dto.setComparisonItems(storedItems);
        }

        if (rankingSummaryList != null) {
            dto.setRankingSummaryList(rankingSummaryList);
        } else if (comparison.getRankingSummary() != null) {
            dto.setRankingSummaryList(Arrays.asList(comparison.getRankingSummary().split("\n")));
        }

        return dto;
    }
}
