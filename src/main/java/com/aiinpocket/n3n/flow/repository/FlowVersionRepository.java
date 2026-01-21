package com.aiinpocket.n3n.flow.repository;

import com.aiinpocket.n3n.flow.entity.FlowVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FlowVersionRepository extends JpaRepository<FlowVersion, UUID> {

    List<FlowVersion> findByFlowIdOrderByCreatedAtDesc(UUID flowId);

    Optional<FlowVersion> findByFlowIdAndVersion(UUID flowId, String version);

    Optional<FlowVersion> findByFlowIdAndStatus(UUID flowId, String status);

    boolean existsByFlowIdAndVersion(UUID flowId, String version);
}
