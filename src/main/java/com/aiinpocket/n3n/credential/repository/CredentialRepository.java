package com.aiinpocket.n3n.credential.repository;

import com.aiinpocket.n3n.credential.entity.Credential;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {

    Page<Credential> findByOwnerId(UUID ownerId, Pageable pageable);

    List<Credential> findByOwnerIdAndType(UUID ownerId, String type);

    @Query("""
        SELECT c FROM Credential c
        WHERE c.ownerId = :userId
        OR c.visibility = 'shared'
        OR c.id IN (
            SELECT cs.credentialId FROM CredentialShare cs WHERE cs.userId = :userId
        )
        """)
    Page<Credential> findAccessibleByUser(@Param("userId") UUID userId, Pageable pageable);

    Optional<Credential> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByNameAndOwnerId(String name, UUID ownerId);

    List<Credential> findByOwnerId(UUID ownerId);

    List<Credential> findByOwnerIdAndKeyVersionLessThan(UUID ownerId, Integer keyVersion);
}
