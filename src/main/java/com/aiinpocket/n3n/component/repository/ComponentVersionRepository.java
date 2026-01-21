package com.aiinpocket.n3n.component.repository;

import com.aiinpocket.n3n.component.entity.ComponentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComponentVersionRepository extends JpaRepository<ComponentVersion, UUID> {

    List<ComponentVersion> findByComponentIdOrderByCreatedAtDesc(UUID componentId);

    Optional<ComponentVersion> findByComponentIdAndVersion(UUID componentId, String version);

    Optional<ComponentVersion> findByComponentIdAndStatus(UUID componentId, String status);

    boolean existsByComponentIdAndVersion(UUID componentId, String version);
}
