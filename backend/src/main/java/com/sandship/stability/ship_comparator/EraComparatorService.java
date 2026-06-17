package com.sandship.stability.ship_comparator;

import com.sandship.stability.dto.ShipComparisonRequest;
import com.sandship.stability.dto.ShipComparisonResultDTO;
import com.sandship.stability.entity.Ship;
import com.sandship.stability.repository.ShipRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class EraComparatorService {

    private static final String CATEGORY_ANCIENT = "ANCIENT";
    private static final String CATEGORY_MODERN = "MODERN";

    private final ShipRepository shipRepository;
    private final ShipComparatorService shipComparatorService;

    @Autowired
    public EraComparatorService(ShipRepository shipRepository, ShipComparatorService shipComparatorService) {
        this.shipRepository = shipRepository;
        this.shipComparatorService = shipComparatorService;
    }

    public ShipComparisonResultDTO compareAncientVsModern(ShipComparisonRequest request) {
        ShipComparisonResultDTO baseResult = shipComparatorService.compareShips(request);

        List<ShipComparisonResultDTO.ShipComparisonItem> items = baseResult.getComparisonItems();
        if (items == null || items.isEmpty()) {
            return baseResult;
        }

        Map<String, BigDecimal> ancientAvgs = new LinkedHashMap<>();
        Map<String, BigDecimal> modernAvgs = new LinkedHashMap<>();
        int ancientCount = 0;
        int modernCount = 0;

        Set<String> metricKeys = items.get(0).getMetrics() != null
                ? items.get(0).getMetrics().keySet()
                : Set.of("GM", "GZ_MAX", "RANGE", "GZ_AREA", "ROLL_PERIOD");

        for (String key : metricKeys) {
            ancientAvgs.put(key, BigDecimal.ZERO);
            modernAvgs.put(key, BigDecimal.ZERO);
        }

        for (ShipComparisonResultDTO.ShipComparisonItem item : items) {
            Map<String, BigDecimal> target = CATEGORY_ANCIENT.equals(item.getCategory()) ? ancientAvgs : modernAvgs;
            if (CATEGORY_ANCIENT.equals(item.getCategory())) ancientCount++;
            else if (CATEGORY_MODERN.equals(item.getCategory())) modernCount++;

            if (item.getMetrics() != null) {
                for (Map.Entry<String, BigDecimal> e : item.getMetrics().entrySet()) {
                    if (target.containsKey(e.getKey())) {
                        target.put(e.getKey(), target.get(e.getKey()).add(e.getValue()));
                    }
                }
            }
        }

        if (ancientCount > 0) {
            ancientAvgs.replaceAll((k, v) -> v.divide(BigDecimal.valueOf(ancientCount), 4, RoundingMode.HALF_UP));
        }
        if (modernCount > 0) {
            modernAvgs.replaceAll((k, v) -> v.divide(BigDecimal.valueOf(modernCount), 4, RoundingMode.HALF_UP));
        }

        Map<String, BigDecimal> eraRatio = new LinkedHashMap<>();
        for (String key : metricKeys) {
            BigDecimal a = ancientAvgs.get(key);
            BigDecimal m = modernAvgs.get(key);
            if (a != null && m != null && a.compareTo(BigDecimal.ZERO) > 0) {
                eraRatio.put(key, m.divide(a, 4, RoundingMode.HALF_UP));
            } else {
                eraRatio.put(key, BigDecimal.ZERO);
            }
        }

        baseResult.setAncientAverageMetrics(ancientAvgs);
        baseResult.setModernAverageMetrics(modernAvgs);
        baseResult.setEraImprovementRatio(eraRatio);
        baseResult.setAncientCount(ancientCount);
        baseResult.setModernCount(modernCount);
        baseResult.setCrossEraComparison(true);

        return baseResult;
    }

    public boolean isCrossEraRequest(ShipComparisonRequest request) {
        if (request == null || request.getShipIds() == null || request.getShipIds().size() < 2) {
            return false;
        }
        List<Ship> ships = shipRepository.findAllById(request.getShipIds());
        boolean hasAncient = ships.stream().anyMatch(s -> CATEGORY_ANCIENT.equals(s.getShipCategory()));
        boolean hasModern = ships.stream().anyMatch(s -> CATEGORY_MODERN.equals(s.getShipCategory()));
        return hasAncient && hasModern;
    }

    public Map<String, Object> getEraSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();

        List<Ship> allShips = shipRepository.findAll();
        List<Ship> ancient = allShips.stream()
                .filter(s -> CATEGORY_ANCIENT.equals(s.getShipCategory()))
                .toList();
        List<Ship> modern = allShips.stream()
                .filter(s -> CATEGORY_MODERN.equals(s.getShipCategory()))
                .toList();

        summary.put("ancientCount", ancient.size());
        summary.put("modernCount", modern.size());
        summary.put("ancientFamilies", ancient.stream()
                .map(Ship::getShipFamily).filter(Objects::nonNull).distinct().toList());
        summary.put("modernFamilies", modern.stream()
                .map(Ship::getShipFamily).filter(Objects::nonNull).distinct().toList());

        if (!ancient.isEmpty() && !modern.isEmpty()) {
            summary.put("avgAncientDisplacement", average(ancient, Ship::getDisplacement));
            summary.put("avgModernDisplacement", average(modern, Ship::getDisplacement));
            summary.put("displacementRatio",
                    average(modern, Ship::getDisplacement)
                            .divide(average(ancient, Ship::getDisplacement), 4, RoundingMode.HALF_UP));
            summary.put("avgAncientGM", average(ancient, Ship::getMetacentricHeightDesign));
            summary.put("avgModernGM", average(modern, Ship::getMetacentricHeightDesign));
            summary.put("gmRatio",
                    average(modern, Ship::getMetacentricHeightDesign)
                            .divide(average(ancient, Ship::getMetacentricHeightDesign), 4, RoundingMode.HALF_UP));
        }

        return summary;
    }

    private BigDecimal average(List<Ship> ships, java.util.function.Function<Ship, BigDecimal> extractor) {
        if (ships == null || ships.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = ships.stream()
                .map(extractor)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(ships.size()), 4, RoundingMode.HALF_UP);
    }
}
