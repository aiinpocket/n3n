package com.aiinpocket.n3n.artifact.repository;

import com.aiinpocket.n3n.artifact.entity.Artifact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArtifactRepository extends JpaRepository<Artifact, UUID> {

    Optional<Artifact> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Artifact> findByExecutionId(UUID executionId);

    Page<Artifact> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    Page<Artifact> findByOwnerIdAndMimeTypeStartingWithOrderByCreatedAtDesc(
            UUID ownerId, String mimeTypePrefix, Pageable pageable);

    @Query("SELECT COALESCE(SUM(a.sizeBytes), 0) FROM Artifact a WHERE a.ownerId = :ownerId")
    long sumSizeBytesByOwnerId(@Param("ownerId") UUID ownerId);

    /**
     * 孤兒 artifacts：掛在已不存在的 execution 下（含單節點試打的臨時 probeId）、
     * 未設為永久且超過保留期限者。
     */
    @Query(value = """
        SELECT a.* FROM artifacts a
        WHERE a.execution_id IS NOT NULL
          AND a.pinned = FALSE
          AND a.created_at < :cutoff
          AND NOT EXISTS (SELECT 1 FROM executions e WHERE e.id = a.execution_id)
        LIMIT :limit
        """, nativeQuery = true)
    java.util.List<Artifact> findOrphanedByMissingExecution(
        @Param("cutoff") java.time.Instant cutoff, @Param("limit") int limit);
}
