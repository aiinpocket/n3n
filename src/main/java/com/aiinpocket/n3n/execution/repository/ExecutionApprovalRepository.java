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
     * Find pending approvals for executions triggered by a specific user.
     * Filters at DB level instead of loading all pending approvals and filtering in memory.
     */
    @Query("SELECT a FROM ExecutionApproval a WHERE a.status = 'pending' " +
           "AND a.executionId IN (SELECT e.id FROM Execution e WHERE e.triggeredBy = :userId) " +
           "ORDER BY a.createdAt DESC")
    List<ExecutionApproval> findPendingByTriggeredUser(@Param("userId") UUID userId);

    /**
     * Check if an approval exists for execution and node
     */
    boolean existsByExecutionIdAndNodeId(UUID executionId, String nodeId);

    /**
     * Atomically increment approved count. Prevents lost updates under concurrent voting.
     *
     * <p>{@code clearAutomatically}/{@code flushAutomatically} are required so the caller's
     * subsequent {@code findById} re-reads the incremented count from the DB instead of
     * returning the stale first-level-cached entity (which would leave approvals unresolvable).</p>
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ExecutionApproval a SET a.approvedCount = a.approvedCount + 1 WHERE a.id = :id")
    void incrementApprovedCount(@Param("id") UUID id);

    /**
     * Atomically increment rejected count. Prevents lost updates under concurrent voting.
     */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE ExecutionApproval a SET a.rejectedCount = a.rejectedCount + 1 WHERE a.id = :id")
    void incrementRejectedCount(@Param("id") UUID id);
}
