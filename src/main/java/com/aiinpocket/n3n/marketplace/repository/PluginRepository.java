package com.aiinpocket.n3n.marketplace.repository;

import com.aiinpocket.n3n.marketplace.entity.Plugin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Plugin entities.
 */
@Repository
public interface PluginRepository extends JpaRepository<Plugin, UUID> {

    /**
     * Find a plugin by its unique name
     */
    Optional<Plugin> findByName(String name);

    /**
     * Check if a plugin name exists
     */
    boolean existsByName(String name);

    /**
     * Find all published plugins
     */
    Page<Plugin> findByPublishedTrue(Pageable pageable);

    /**
     * Find published plugins by type
     */
    Page<Plugin> findByPublishedTrueAndType(Plugin.PluginType type, Pageable pageable);

    /**
     * Find published plugins by category
     */
    Page<Plugin> findByPublishedTrueAndCategory(String category, Pageable pageable);

    /**
     * Find featured plugins
     */
    List<Plugin> findByPublishedTrueAndFeaturedTrueOrderByDownloadCountDesc(Pageable pageable);

    /**
     * Find plugins by author
     */
    List<Plugin> findByAuthorId(UUID authorId);

    /**
     * Search plugins by name or description
     */
    @Query("SELECT p FROM Plugin p WHERE p.published = true AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.displayName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Plugin> search(@Param("query") String query, Pageable pageable);

    /**
     * Find popular plugins (by download count)
     */
    @Query("SELECT p FROM Plugin p WHERE p.published = true ORDER BY p.downloadCount DESC")
    List<Plugin> findPopular(Pageable pageable);

    /**
     * Find trending plugins (by weekly downloads)
     */
    @Query("SELECT p FROM Plugin p WHERE p.published = true ORDER BY p.weeklyDownloads DESC")
    List<Plugin> findTrending(Pageable pageable);

    /**
     * Find recently updated plugins
     */
    @Query("SELECT p FROM Plugin p WHERE p.published = true ORDER BY p.updatedAt DESC")
    List<Plugin> findRecentlyUpdated(Pageable pageable);

    /**
     * Find top rated plugins
     */
    @Query("SELECT p FROM Plugin p WHERE p.published = true AND p.ratingCount >= :minRatings ORDER BY p.ratingAvg DESC")
    List<Plugin> findTopRated(@Param("minRatings") int minRatings, Pageable pageable);

    /**
     * Count plugins by type
     */
    long countByPublishedTrueAndType(Plugin.PluginType type);

    /**
     * Get all categories
     */
    @Query("SELECT DISTINCT p.category FROM Plugin p WHERE p.published = true AND p.category IS NOT NULL")
    List<String> findAllCategories();
}
