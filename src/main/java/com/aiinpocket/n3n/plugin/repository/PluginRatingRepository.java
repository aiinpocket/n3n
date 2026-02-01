package com.aiinpocket.n3n.plugin.repository;

import com.aiinpocket.n3n.plugin.entity.PluginRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PluginRatingRepository extends JpaRepository<PluginRating, UUID> {

    List<PluginRating> findByPluginId(UUID pluginId);

    Optional<PluginRating> findByPluginIdAndUserId(UUID pluginId, UUID userId);

    @Query("SELECT AVG(pr.rating) FROM PluginRating pr WHERE pr.pluginId = :pluginId")
    Double getAverageRating(@Param("pluginId") UUID pluginId);

    @Query("SELECT COUNT(pr) FROM PluginRating pr WHERE pr.pluginId = :pluginId")
    Long getRatingCount(@Param("pluginId") UUID pluginId);
}
