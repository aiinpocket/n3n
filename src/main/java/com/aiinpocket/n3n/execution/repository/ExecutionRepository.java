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

    Page<Execution> findByTriggeredByOrderByStartedAtDesc(UUID triggeredBy, Pageable pageable);

    List<Execution> findByFlowVersionIdAndStatus(UUID flowVersionId, String status);

    Page<Execution> findAllByOrderByStartedAtDesc(Pageable pageable);

    Page<Execution> findByTriggeredByAndStatusOrderByStartedAtDesc(UUID triggeredBy, String status, Pageable pageable);

    Page<Execution> findByStatusOrderByStartedAtDesc(String status, Pageable pageable);

    @Query(value = "SELECT e.* FROM executions e " +
        "JOIN flow_versions fv ON e.flow_version_id = fv.id " +
        "JOIN flows f ON fv.flow_id = f.id " +
        "WHERE LOWER(f.name) LIKE LOWER(CONCAT('%', CAST(:search AS TEXT), '%')) " +
        "ORDER BY e.started_at DESC",
        countQuery = "SELECT COUNT(*) FROM executions e " +
            "JOIN flow_versions fv ON e.flow_version_id = fv.id " +
            "JOIN flows f ON fv.flow_id = f.id " +
            "WHERE LOWER(f.name) LIKE LOWER(CONCAT('%', CAST(:search AS TEXT), '%'))",
        nativeQuery = true)
    Page<Execution> findByFlowNameContaining(@Param("search") String search, Pageable pageable);

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
        "WHERE e.status = CAST(:status AS TEXT) AND LOWER(f.name) LIKE LOWER(CONCAT('%', CAST(:search AS TEXT), '%')) " +
        "ORDER BY e.started_at DESC",
        countQuery = "SELECT COUNT(*) FROM executions e " +
            "JOIN flow_versions fv ON e.flow_version_id = fv.id " +
            "JOIN flows f ON fv.flow_id = f.id " +
            "WHERE e.status = CAST(:status AS TEXT) AND LOWER(f.name) LIKE LOWER(CONCAT('%', CAST(:search AS TEXT), '%'))",
        nativeQuery = true)
    Page<Execution> findByStatusAndFlowNameContaining(@Param("status") String status, @Param("search") String search, Pageable pageable);

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
     * Count all executions by a specific user.
     */
    long countByTriggeredBy(UUID userId);

    /**
     * Count executions by a specific user and status.
     */
    long countByTriggeredByAndStatus(UUID userId, String status);

    /**
     * Count running executions for a flow version.
     */
    long countByFlowVersionIdAndStatus(UUID flowVersionId, String status);

    /**
     * Count executions by status (all time).
     */
    long countByStatus(String status);

    /**
     * Count executions started after a given time.
     */
    long countByStartedAtAfter(Instant after);

    /**
     * Count executions by status started after a given time.
     */
    long countByStatusAndStartedAtAfter(String status, Instant after);

    /**
     * Calculate average duration for completed executions started after a given time.
     */
    @Query("SELECT AVG(e.durationMs) FROM Execution e WHERE e.status = 'completed' AND e.durationMs IS NOT NULL AND e.startedAt > :after")
    Double findAverageDurationMsSince(@Param("after") Instant after);
}
