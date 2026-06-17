package com.sandship.stability.repository;

import com.sandship.stability.entity.VirtualLoadingSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VirtualLoadingSessionRepository extends JpaRepository<VirtualLoadingSession, UUID> {

    List<VirtualLoadingSession> findByShipIdAndIsActiveOrderByLastActivityDesc(UUID shipId, boolean isActive);

    List<VirtualLoadingSession> findByUserIdOrderByLastActivityDesc(String userId);

    Page<VirtualLoadingSession> findByIsPublicTrueOrderByCreatedAtDesc(Pageable pageable);

    Optional<VirtualLoadingSession> findTopByShipIdAndIsActiveOrderByLastActivityDesc(UUID shipId);
}
