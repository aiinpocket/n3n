package com.aiinpocket.n3n.execution.repository;

import com.aiinpocket.n3n.execution.entity.ExecutionArchive;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface ExecutionArchiveRepository extends JpaRepository<ExecutionArchive, UUID> {

    Page<ExecutionArchive> findByFlowVersionIdOrderByStartedAtDesc(UUID flowVersionId, Pageable pageable);

    Page<ExecutionArchive> findByTriggeredByOrderByStartedAtDesc(UUID triggeredBy, Pageable pageable);

    @Modifying
    @Query("DELETE FROM ExecutionArchive a WHERE a.archivedAt < :before")
    int deleteByArchivedAtBefore(@Param("before") Instant before);
}
