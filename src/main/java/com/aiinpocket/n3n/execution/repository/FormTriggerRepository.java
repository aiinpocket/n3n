package com.aiinpocket.n3n.execution.repository;

import com.aiinpocket.n3n.execution.entity.FormTrigger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FormTriggerRepository extends JpaRepository<FormTrigger, UUID> {

    /**
     * Find form trigger by token (for public form access)
     */
    Optional<FormTrigger> findByFormToken(String formToken);

    /**
     * Find form trigger by flow ID and node ID
     */
    Optional<FormTrigger> findByFlowIdAndNodeId(UUID flowId, String nodeId);

    /**
     * Find all form triggers for a flow
     */
    List<FormTrigger> findByFlowId(UUID flowId);

    /**
     * Find all active form triggers for a flow
     */
    @Query("SELECT f FROM FormTrigger f WHERE f.flowId = :flowId AND f.isActive = true")
    List<FormTrigger> findActiveByFlowId(@Param("flowId") UUID flowId);

    /**
     * Find all expired form triggers
     */
    @Query("SELECT f FROM FormTrigger f WHERE f.isActive = true AND f.expiresAt IS NOT NULL AND f.expiresAt < :now")
    List<FormTrigger> findExpiredTriggers(@Param("now") Instant now);

    /**
     * Find form triggers created by a user
     */
    List<FormTrigger> findByCreatedByOrderByCreatedAtDesc(UUID createdBy);

    /**
     * Check if form token exists
     */
    boolean existsByFormToken(String formToken);
}
