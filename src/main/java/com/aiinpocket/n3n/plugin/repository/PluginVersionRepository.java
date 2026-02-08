package com.aiinpocket.n3n.plugin.repository;

import com.aiinpocket.n3n.plugin.entity.PluginVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository("pluginPluginVersionRepository")
public interface PluginVersionRepository extends JpaRepository<PluginVersion, UUID> {

    List<PluginVersion> findByPluginIdOrderByPublishedAtDesc(UUID pluginId);

    Optional<PluginVersion> findByPluginIdAndVersion(UUID pluginId, String version);

    @Query("SELECT pv FROM PluginVersion pv WHERE pv.pluginId = :pluginId " +
            "ORDER BY pv.publishedAt DESC LIMIT 1")
    Optional<PluginVersion> findLatestByPluginId(@Param("pluginId") UUID pluginId);

    @Query("SELECT SUM(pv.downloadCount) FROM PluginVersion pv WHERE pv.pluginId = :pluginId")
    Long getTotalDownloads(@Param("pluginId") UUID pluginId);

    @Query("SELECT pv FROM PluginVersion pv WHERE pv.pluginId IN :pluginIds " +
            "AND pv.publishedAt = (SELECT MAX(pv2.publishedAt) FROM PluginVersion pv2 WHERE pv2.pluginId = pv.pluginId)")
    List<PluginVersion> findLatestByPluginIds(@Param("pluginIds") Collection<UUID> pluginIds);

    @Query("SELECT pv.pluginId, SUM(pv.downloadCount) FROM PluginVersion pv WHERE pv.pluginId IN :pluginIds GROUP BY pv.pluginId")
    List<Object[]> getTotalDownloadsByPluginIds(@Param("pluginIds") Collection<UUID> pluginIds);

    @Modifying
    @Query("UPDATE PluginVersion pv SET pv.downloadCount = pv.downloadCount + 1 WHERE pv.id = :id")
    void incrementDownloadCount(@Param("id") UUID id);
}
