package com.aiinpocket.n3n.credential.repository;

import com.aiinpocket.n3n.credential.entity.CredentialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CredentialTypeRepository extends JpaRepository<CredentialType, UUID> {

    Optional<CredentialType> findByName(String name);

    boolean existsByName(String name);
}
