package com.aiinpocket.n3n.execution.repository;

import com.aiinpocket.n3n.execution.entity.NodeExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NodeExecutionRepository extends JpaRepository<NodeExecution, UUID> {

    List<NodeExecution> findByExecutionIdOrderByStartedAtAsc(UUID executionId);

    Optional<NodeExecution> findByExecutionIdAndNodeId(UUID executionId, String nodeId);

    @Modifying
    void deleteByExecutionId(UUID executionId);
}
