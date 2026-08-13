package com.aiinpocket.n3n.site.repository;

import com.aiinpocket.n3n.site.entity.Site;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SiteRepository extends JpaRepository<Site, UUID> {

    List<Site> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    Optional<Site> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<Site> findBySlug(String slug);

    boolean existsBySlug(String slug);

    long countByOwnerId(UUID ownerId);

    Optional<Site> findByCustomDomainAndCustomDomainVerifiedTrue(String customDomain);

    boolean existsByCustomDomain(String customDomain);
}
