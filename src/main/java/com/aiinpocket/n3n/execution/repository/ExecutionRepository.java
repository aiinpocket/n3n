package com.aiinpocket.n3n.execution.repository;

import com.aiinpocket.n3n.execution.entity.Execution;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExecutionRepository extends JpaRepository<Execution, UUID> {

    Page<Execution> findByFlowVersionIdOrderByStartedAtDesc(UUID flowVersionId, Pageable pageable);

    Page<Execution> findByFlowVersionIdAndTriggeredByOrderByStartedAtDesc(UUID flowVersionId, UUID triggeredBy, Pageable pageable);

    Page<Execution> findByTriggeredByOrderByStartedAtDesc(UUID triggeredBy, Pageable pageable);

    List<Execution> findByFlowVersionIdAndStatus(UUID flowVersionId, String status);

    Page<Execution> findByTriggeredByAndStatusOrderByStartedAtDesc(UUID triggeredBy, String status, Pageable pageable);

    @Query(value = "SELECT e.* FROM executions e " +
        "JOIN flow_versions fv ON e.flow_version_id = fv.id " +
        "JOIN flows f ON fv.flow_id = f.id " +
        "WHERE e.triggered_by = :userId AND LOWER(f.name) LIKE LOWER(CONCAT('%', CAST(:search AS TEXT), '%')) " +
        "ORDER BY e.started_at DESC",
        countQuery = "SELECT COUNT(*) FROM executions e " +
            "JOIN flow_versions fv ON e.flow_version_id = fv.id " +
            "JOIN flows f ON fv.flow_id = f.id " +
            "WHERE e.triggered_by = :userId AND LOWER(f.name) LIKE LOWER(CONCAT('%', CAST(:search AS TEXT), '%'))",
        nativeQuery = true)
    Page<Execution> findByUserAndFlowNameContaining(@Param("userId") UUID userId, @Param("search") String search, Pageable pageable);

    @Query(value = "SELECT e.* FROM executions e " +
        "JOIN flow_versions fv ON e.flow_version_id = fv.id " +
        "JOIN flows f ON fv.flow_id = f.id " +
        "WHERE e.triggered_by = :userId AND e.status = CAST(:status AS TEXT) AND LOWER(f.name) LIKE LOWER(CONCAT('%', CAST(:search AS TEXT), '%')) " +
        "ORDER BY e.started_at DESC",
        countQuery = "SELECT COUNT(*) FROM executions e " +
            "JOIN flow_versions fv ON e.flow_version_id = fv.id " +
            "JOIN flows f ON fv.flow_id = f.id " +
            "WHERE e.triggered_by = :userId AND e.status = CAST(:status AS TEXT) AND LOWER(f.name) LIKE LOWER(CONCAT('%', CAST(:search AS TEXT), '%'))",
        nativeQuery = true)
    Page<Execution> findByUserAndStatusAndFlowNameContaining(@Param("userId") UUID userId, @Param("status") String status, @Param("search") String search, Pageable pageable);

    @Query(value = "SELECT e FROM Execution e WHERE e.completedAt < :cutoff AND e.status IN :statuses ORDER BY e.completedAt ASC LIMIT :limit")
    List<Execution> findByCompletedAtBeforeAndStatusIn(
        @Param("cutoff") Instant cutoff,
        @Param("statuses") Collection<String> statuses,
        @Param("limit") int limit);

    /**
     * Find executions by status and started before date (for housekeeping).
     */
    Page<Execution> findByStatusInAndStartedAtBefore(
        Collection<String> statuses,
        Instant startedBefore,
        Pageable pageable);

    /**
     * Aggregate user-specific execution statistics in a single query for dashboard.
     * Returns: [total, completed, failed, running]
     */
    @Query("SELECT " +
            "COUNT(e), " +
            "COUNT(CASE WHEN e.status = 'completed' THEN 1 END), " +
            "COUNT(CASE WHEN e.status = 'failed' THEN 1 END), " +
            "COUNT(CASE WHEN e.status = 'running' THEN 1 END) " +
            "FROM Execution e WHERE e.triggeredBy = :userId")
    Object[] getUserDashboardStats(@Param("userId") UUID userId);

    /**
     * Count executions started before a given time (for housekeeping stats).
     */
    long countByStartedAtBefore(Instant before);

    /**
     * Count executions by status (for concurrent limit enforcement).
     */
    long countByStatus(String status);

    /**
     * Find stuck executions: running but started before cutoff (for timeout monitor).
     * Limited to 1000 to prevent unbounded query in pathological cases.
     */
    List<Execution> findTop1000ByStatusAndStartedAtBefore(String status, Instant before);

    /**
     * Find all executions with a given status (for startup recovery).
     * Limited to 1000 to prevent unbounded query.
     */
    List<Execution> findTop1000ByStatus(String status);

    /**
     * Count running executions for a specific user (for per-user concurrent limit).
     */
    long countByStatusAndTriggeredBy(String status, UUID triggeredBy);

    /**
     * Check if an execution exists and is owned by the given user.
     */
    boolean existsByIdAndTriggeredBy(UUID id, UUID triggeredBy);

    /**
     * Aggregate execution statistics in a single query for monitoring dashboard.
     * Returns: [total24h, running, completed24h, failed24h, cancelled24h, avgDurationMs, totalAllTime]
     */
    @Query("SELECT " +
            "COUNT(CASE WHEN e.startedAt > :after THEN 1 END), " +
            "COUNT(CASE WHEN e.status = 'running' THEN 1 END), " +
            "COUNT(CASE WHEN e.status = 'completed' AND e.startedAt > :after THEN 1 END), " +
            "COUNT(CASE WHEN e.status = 'failed' AND e.startedAt > :after THEN 1 END), " +
            "COUNT(CASE WHEN e.status = 'cancelled' AND e.startedAt > :after THEN 1 END), " +
            "AVG(CASE WHEN e.status = 'completed' AND e.durationMs IS NOT NULL AND e.startedAt > :after THEN e.durationMs END), " +
            "COUNT(e) " +
            "FROM Execution e")
    Object[] getAggregatedStats(@Param("after") Instant after);
}
