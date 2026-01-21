package com.aiinpocket.n3n.auth.repository;

import com.aiinpocket.n3n.auth.entity.EmailVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {

    Optional<EmailVerification> findByTokenHashAndTypeAndUsedAtIsNull(String tokenHash, String type);
}
