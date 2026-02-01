package com.aiinpocket.n3n.plugin.repository;

import com.aiinpocket.n3n.plugin.entity.PluginInstallation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PluginInstallationRepository extends JpaRepository<PluginInstallation, UUID> {

    List<PluginInstallation> findByUserId(UUID userId);

    List<PluginInstallation> findByUserIdAndIsEnabledTrue(UUID userId);

    Optional<PluginInstallation> findByPluginIdAndUserId(UUID pluginId, UUID userId);

    boolean existsByPluginIdAndUserId(UUID pluginId, UUID userId);

    @Query("SELECT pi FROM PluginInstallation pi " +
            "JOIN FETCH pi.plugin p " +
            "JOIN FETCH pi.pluginVersion pv " +
            "WHERE pi.userId = :userId")
    List<PluginInstallation> findByUserIdWithDetails(@Param("userId") UUID userId);

    @Query("SELECT pi FROM PluginInstallation pi " +
            "JOIN FETCH pi.plugin p " +
            "JOIN FETCH pi.pluginVersion pv " +
            "WHERE pi.userId = :userId AND pi.isEnabled = true")
    List<PluginInstallation> findEnabledByUserIdWithDetails(@Param("userId") UUID userId);

    void deleteByPluginIdAndUserId(UUID pluginId, UUID userId);
}
