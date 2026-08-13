package com.aiinpocket.n3n.hostedapp.repository;

import com.aiinpocket.n3n.hostedapp.entity.HostedApp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HostedAppRepository extends JpaRepository<HostedApp, UUID> {

    List<HostedApp> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);

    Optional<HostedApp> findByIdAndOwnerId(UUID id, UUID ownerId);

    long countByOwnerId(UUID ownerId);

    boolean existsBySlug(String slug);

    Optional<HostedApp> findBySlug(String slug);

    List<HostedApp> findByHostPortIsNotNull();
}
