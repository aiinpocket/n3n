package com.aiinpocket.n3n.oauth2.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity for storing OAuth2 tokens associated with credentials.
 */
@Entity
@Table(name = "oauth2_tokens")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuth2Token {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "credential_id", nullable = false)
    private UUID credentialId;

    @Column(name = "provider", nullable = false, length = 50)
    private String provider;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "token_type", length = 50)
    private String tokenType;

    @Column(name = "scope", length = 500)
    private String scope;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Check if the token is expired.
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if the token will expire soon (within 5 minutes).
     */
    public boolean isExpiringSoon() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().plusSeconds(300).isAfter(expiresAt);
    }
}
