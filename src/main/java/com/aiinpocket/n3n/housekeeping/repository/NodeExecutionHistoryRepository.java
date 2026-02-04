package com.aiinpocket.n3n.housekeeping.repository;

import com.aiinpocket.n3n.housekeeping.entity.NodeExecutionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface NodeExecutionHistoryRepository extends JpaRepository<NodeExecutionHistory, UUID> {

    /**
     * Find node executions by execution ID.
     */
    List<NodeExecutionHistory> findByExecutionIdOrderByStartedAt(UUID executionId);

    /**
     * Delete node executions by execution ID.
     */
    @Modifying
    @Query("DELETE FROM NodeExecutionHistory n WHERE n.executionId = :executionId")
    int deleteByExecutionId(@Param("executionId") UUID executionId);

    /**
     * Delete old history records.
     */
    @Modifying
    @Query("DELETE FROM NodeExecutionHistory n WHERE n.archivedAt < :cutoffDate")
    int deleteByArchivedAtBefore(@Param("cutoffDate") Instant cutoffDate);
}
