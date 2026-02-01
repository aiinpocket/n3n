package com.aiinpocket.n3n.marketplace.repository;

import com.aiinpocket.n3n.marketplace.entity.PluginVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for PluginVersion entities.
 */
@Repository
public interface PluginVersionRepository extends JpaRepository<PluginVersion, UUID> {

    /**
     * Find all versions for a plugin
     */
    List<PluginVersion> findByPluginIdOrderByPublishedAtDesc(UUID pluginId);

    /**
     * Find a specific version
     */
    Optional<PluginVersion> findByPluginIdAndVersion(UUID pluginId, String version);

    /**
     * Find the latest non-yanked version
     */
    Optional<PluginVersion> findFirstByPluginIdAndYankedFalseOrderByPublishedAtDesc(UUID pluginId);

    /**
     * Find the latest stable (non-prerelease) version
     */
    Optional<PluginVersion> findFirstByPluginIdAndPrereleaseFalseAndYankedFalseOrderByPublishedAtDesc(UUID pluginId);

    /**
     * Check if a version exists
     */
    boolean existsByPluginIdAndVersion(UUID pluginId, String version);
}
