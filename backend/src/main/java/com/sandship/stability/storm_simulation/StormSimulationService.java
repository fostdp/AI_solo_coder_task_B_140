package com.sandship.stability.storm_simulation;

import com.sandship.stability.dto.StormSimulationRequest;
import com.sandship.stability.dto.StormSimulationResultDTO;
import com.sandship.stability.storm_simulator.StormSimulatorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Deprecated(since = "2.0", forRemoval = true)
@Service
public class StormSimulationService {

    private static final String DEPRECATION_WARN = "[StormSimulation] 该服务已弃用，请使用 StormSimulatorService 替代。" +
            "原服务将在后续版本中移除。";

    @Autowired
    private StormSimulatorService stormSimulatorService;

    @Transactional
    public StormSimulationResultDTO simulateStorm(UUID shipId, StormSimulationRequest request) {
        log.warn(DEPRECATION_WARN);
        return stormSimulatorService.simulateStorm(shipId, request);
    }

    @Transactional
    public List<StormSimulationResultDTO> simulateBatch(StormSimulationRequest request) {
        log.warn(DEPRECATION_WARN);
        return stormSimulatorService.simulateBatch(request);
    }

    public List<StormSimulationResultDTO> getSimulationHistory(UUID shipId, int limit) {
        log.warn(DEPRECATION_WARN);
        return stormSimulatorService.getSimulationHistory(shipId, limit);
    }

    public List<StormSimulationResultDTO> getPublicSimulations() {
        log.warn(DEPRECATION_WARN);
        return stormSimulatorService.getPublicSimulations();
    }

    public Optional<StormSimulationResultDTO> getSimulationById(UUID id) {
        log.warn(DEPRECATION_WARN);
        return stormSimulatorService.getSimulationById(id);
    }

    public Optional<StormSimulationResultDTO> getLatestSimulation(UUID shipId) {
        log.warn(DEPRECATION_WARN);
        return stormSimulatorService.getLatestSimulation(shipId);
    }

    @Transactional
    public boolean deleteSimulation(UUID id) {
        log.warn(DEPRECATION_WARN);
        return stormSimulatorService.deleteSimulation(id);
    }

    public Map<String, Object> getStatistics(UUID shipId) {
        log.warn(DEPRECATION_WARN);
        return stormSimulatorService.getStatistics(shipId);
    }
}
