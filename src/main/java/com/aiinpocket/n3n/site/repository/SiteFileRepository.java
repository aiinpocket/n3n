package com.aiinpocket.n3n.site.repository;

import com.aiinpocket.n3n.site.entity.SiteFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiteFileRepository extends JpaRepository<SiteFile, UUID> {

    List<SiteFile> findBySiteIdOrderByPathAsc(UUID siteId);

    Optional<SiteFile> findBySiteIdAndPath(UUID siteId, String path);

    long countBySiteId(UUID siteId);

    @Query("SELECT COALESCE(SUM(f.sizeBytes), 0) FROM SiteFile f WHERE f.siteId = :siteId")
    long sumSizeBytesBySiteId(@Param("siteId") UUID siteId);

    @Modifying
    @Query("DELETE FROM SiteFile f WHERE f.siteId = :siteId")
    void deleteBySiteId(@Param("siteId") UUID siteId);

    @Modifying
    @Query("DELETE FROM SiteFile f WHERE f.siteId = :siteId AND f.path = :path")
    int deleteBySiteIdAndPath(@Param("siteId") UUID siteId, @Param("path") String path);
}
