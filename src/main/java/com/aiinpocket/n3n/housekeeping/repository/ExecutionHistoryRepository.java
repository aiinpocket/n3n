package com.aiinpocket.n3n.housekeeping.repository;

import com.aiinpocket.n3n.housekeeping.entity.ExecutionHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExecutionHistoryRepository extends JpaRepository<ExecutionHistory, UUID> {

    /**
     * Find executions by flow ID.
     */
    Page<ExecutionHistory> findByFlowIdOrderByStartedAtDesc(UUID flowId, Pageable pageable);

    /**
     * Find executions by status.
     */
    Page<ExecutionHistory> findByStatusOrderByArchivedAtDesc(String status, Pageable pageable);

    /**
     * Find executions archived before a date.
     */
    @Query("SELECT e FROM ExecutionHistory e WHERE e.archivedAt < :cutoffDate")
    List<UUID> findIdsArchivedBefore(@Param("cutoffDate") Instant cutoffDate);

    /**
     * Delete old history records.
     */
    @Modifying
    @Query("DELETE FROM ExecutionHistory e WHERE e.archivedAt < :cutoffDate")
    int deleteByArchivedAtBefore(@Param("cutoffDate") Instant cutoffDate);

    /**
     * Count records in history.
     */
    long countByFlowId(UUID flowId);
}
