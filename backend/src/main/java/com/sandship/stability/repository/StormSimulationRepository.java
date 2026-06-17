package com.sandship.stability.repository;

import com.sandship.stability.entity.StormSimulation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StormSimulationRepository extends JpaRepository<StormSimulation, UUID> {

    List<StormSimulation> findByShipIdOrderBySimulationTimeDesc(UUID shipId);

    Optional<StormSimulation> findTopByShipIdOrderBySimulationTimeDesc(UUID shipId);

    List<StormSimulation> findByShipIdAndSimulationTimeBetweenOrderBySimulationTimeDesc(
            UUID shipId, LocalDateTime startTime, LocalDateTime endTime);

    List<StormSimulation> findTop10ByOrderBySimulationTimeDesc();
}
