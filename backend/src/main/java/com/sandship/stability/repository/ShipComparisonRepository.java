package com.sandship.stability.repository;

import com.sandship.stability.entity.ShipComparison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShipComparisonRepository extends JpaRepository<ShipComparison, UUID> {

    @Query(value = "SELECT * FROM ship_comparisons " +
                   "WHERE ship_ids @> CAST(:shipId AS text)::jsonb " +
                   "ORDER BY created_at DESC", nativeQuery = true)
    List<ShipComparison> findByShipIdsContaining(@Param("shipId") String shipId);

    List<ShipComparison> findTop10ByOrderByCreatedAtDesc();

    List<ShipComparison> findByCreatedByOrderByCreatedAtDesc(String createdBy);
}
