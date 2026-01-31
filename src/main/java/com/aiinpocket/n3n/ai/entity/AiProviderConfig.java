package com.aiinpocket.n3n.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * AI Provider 設定實體
 */
@Entity
@Table(name = "ai_provider_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "workspace_id")
    private UUID workspaceId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "credential_id")
    private UUID credentialId;

    @Column(name = "base_url")
    private String baseUrl;

    @Column(name = "default_model")
    private String defaultModel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> settings = Map.of();

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    @Column(name = "rate_limit_rpm")
    private Integer rateLimitRpm;

    @Column(name = "rate_limit_tpm")
    private Integer rateLimitTpm;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
