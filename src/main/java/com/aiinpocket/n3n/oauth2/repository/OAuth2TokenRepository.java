package com.aiinpocket.n3n.oauth2.repository;

import com.aiinpocket.n3n.oauth2.entity.OAuth2Token;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuth2TokenRepository extends JpaRepository<OAuth2Token, UUID> {

    Optional<OAuth2Token> findByCredentialId(UUID credentialId);

    void deleteByCredentialId(UUID credentialId);
}
