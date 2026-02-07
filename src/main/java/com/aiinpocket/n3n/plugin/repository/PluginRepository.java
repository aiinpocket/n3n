package com.aiinpocket.n3n.plugin.repository;

import com.aiinpocket.n3n.plugin.entity.Plugin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository("pluginPluginRepository")
public interface PluginRepository extends JpaRepository<Plugin, UUID> {

    Optional<Plugin> findByName(String name);

    List<Plugin> findByCategory(String category);

    Page<Plugin> findByCategory(String category, Pageable pageable);

    @Query(value = "SELECT * FROM plugins p WHERE " +
            "(CAST(:category AS TEXT) IS NULL OR p.category = CAST(:category AS TEXT)) AND " +
            "(CAST(:pricing AS TEXT) IS NULL OR p.pricing = CAST(:pricing AS TEXT)) AND " +
            "(CAST(:query AS TEXT) IS NULL OR LOWER(p.display_name) LIKE LOWER('%' || CAST(:query AS TEXT) || '%') OR " +
            "LOWER(p.description) LIKE LOWER('%' || CAST(:query AS TEXT) || '%'))",
            countQuery = "SELECT COUNT(*) FROM plugins p WHERE " +
            "(CAST(:category AS TEXT) IS NULL OR p.category = CAST(:category AS TEXT)) AND " +
            "(CAST(:pricing AS TEXT) IS NULL OR p.pricing = CAST(:pricing AS TEXT)) AND " +
            "(CAST(:query AS TEXT) IS NULL OR LOWER(p.display_name) LIKE LOWER('%' || CAST(:query AS TEXT) || '%') OR " +
            "LOWER(p.description) LIKE LOWER('%' || CAST(:query AS TEXT) || '%'))",
            nativeQuery = true)
    Page<Plugin> searchPlugins(
            @Param("category") String category,
            @Param("pricing") String pricing,
            @Param("query") String query,
            Pageable pageable);

    @Query("SELECT DISTINCT p.category FROM Plugin p ORDER BY p.category")
    List<String> findAllCategories();

    @Query(value = "SELECT * FROM plugins WHERE CAST(:tag AS TEXT) = ANY(tags)", nativeQuery = true)
    List<Plugin> findByTag(@Param("tag") String tag);
}
