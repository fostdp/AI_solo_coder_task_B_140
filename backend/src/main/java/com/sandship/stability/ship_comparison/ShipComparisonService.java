package com.sandship.stability.ship_comparison;

import com.sandship.stability.dto.ShipComparisonRequest;
import com.sandship.stability.dto.ShipComparisonResultDTO;
import com.sandship.stability.dto.ShipDTO;
import com.sandship.stability.ship_comparator.EraComparatorService;
import com.sandship.stability.ship_comparator.ShipComparatorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Deprecated
public class ShipComparisonService {

    private final ShipComparatorService shipComparatorService;
    private final EraComparatorService eraComparatorService;

    @Autowired
    public ShipComparisonService(@Lazy ShipComparatorService shipComparatorService,
                                 @Lazy EraComparatorService eraComparatorService) {
        this.shipComparatorService = shipComparatorService;
        this.eraComparatorService = eraComparatorService;
        log.warn("[ShipComparisonService] DEPRECATED - 请使用 ShipComparatorService / EraComparatorService");
    }

    @Transactional
    public ShipComparisonResultDTO compareShips(ShipComparisonRequest request) {
        if (eraComparatorService.isCrossEraRequest(request)) {
            return eraComparatorService.compareAncientVsModern(request);
        }
        return shipComparatorService.compareShips(request);
    }

    public List<ShipComparisonResultDTO> getComparisonHistory(int limit) {
        return shipComparatorService.getComparisonHistory(limit);
    }

    public ShipComparisonResultDTO getComparisonById(UUID id) {
        return shipComparatorService.getComparisonById(id);
    }

    @Transactional
    public void deleteComparison(UUID id) {
        shipComparatorService.deleteComparison(id);
    }

    public List<ShipDTO> getAvailableShips() {
        return shipComparatorService.getAvailableShips();
    }
}
