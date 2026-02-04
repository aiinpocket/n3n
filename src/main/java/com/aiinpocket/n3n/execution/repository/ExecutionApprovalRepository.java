package com.aiinpocket.n3n.execution.repository;

import com.aiinpocket.n3n.execution.entity.ExecutionApproval;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExecutionApprovalRepository extends JpaRepository<ExecutionApproval, UUID> {

    /**
     * Find approval by execution ID and node ID
     */
    Optional<ExecutionApproval> findByExecutionIdAndNodeId(UUID executionId, String nodeId);

    /**
     * Find all approvals for an execution
     */
    List<ExecutionApproval> findByExecutionId(UUID executionId);

    /**
     * Find pending approval by execution ID
     */
    @Query("SELECT a FROM ExecutionApproval a WHERE a.executionId = :executionId AND a.status = 'pending'")
    Optional<ExecutionApproval> findPendingByExecutionId(@Param("executionId") UUID executionId);

    /**
     * Find all expired pending approvals
     */
    @Query("SELECT a FROM ExecutionApproval a WHERE a.status = 'pending' AND a.expiresAt IS NOT NULL AND a.expiresAt < :now")
    List<ExecutionApproval> findExpiredApprovals(@Param("now") Instant now);

    /**
     * Find all pending approvals
     */
    @Query("SELECT a FROM ExecutionApproval a WHERE a.status = 'pending' ORDER BY a.createdAt DESC")
    List<ExecutionApproval> findAllPending();

    /**
     * Check if an approval exists for execution and node
     */
    boolean existsByExecutionIdAndNodeId(UUID executionId, String nodeId);
}
