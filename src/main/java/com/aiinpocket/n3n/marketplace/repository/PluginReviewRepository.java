package com.aiinpocket.n3n.marketplace.repository;

import com.aiinpocket.n3n.marketplace.entity.PluginReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for PluginReview entities.
 */
@Repository("marketplacePluginReviewRepository")
public interface PluginReviewRepository extends JpaRepository<PluginReview, UUID> {

    /**
     * Find reviews for a plugin
     */
    Page<PluginReview> findByPluginIdOrderByCreatedAtDesc(UUID pluginId, Pageable pageable);

    /**
     * Find a user's review for a plugin
     */
    Optional<PluginReview> findByPluginIdAndUserId(UUID pluginId, UUID userId);

    /**
     * Check if user has reviewed a plugin
     */
    boolean existsByPluginIdAndUserId(UUID pluginId, UUID userId);

    /**
     * Get average rating for a plugin
     */
    @Query("SELECT AVG(r.rating) FROM MarketplacePluginReview r WHERE r.plugin.id = :pluginId")
    Double getAverageRating(@Param("pluginId") UUID pluginId);

    /**
     * Count reviews for a plugin
     */
    long countByPluginId(UUID pluginId);

    /**
     * Find reviews by rating
     */
    Page<PluginReview> findByPluginIdAndRating(UUID pluginId, int rating, Pageable pageable);

    /**
     * Find helpful reviews
     */
    Page<PluginReview> findByPluginIdOrderByHelpfulCountDesc(UUID pluginId, Pageable pageable);
}
