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
