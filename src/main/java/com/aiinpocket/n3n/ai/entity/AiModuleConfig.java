package com.aiinpocket.n3n.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity for storing user's AI module configurations.
 * Each user can have different AI providers for different AI features.
 *
 * Features: flowOptimization, naturalLanguage
 */
@Entity
@Table(name = "ai_module_configs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "feature"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiModuleConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * Feature this config applies to: flowOptimization, naturalLanguage
     */
    @Column(nullable = false)
    private String feature;

    /**
     * Provider type: llamafile, openai, ollama, gemini, claude
     */
    @Column(name = "provider_type", nullable = false)
    @Builder.Default
    private String providerType = "llamafile";

    /**
     * Display name for this configuration
     */
    @Column(name = "display_name")
    private String displayName;

    /**
     * Base URL for the provider API (for ollama, custom endpoints)
     */
    @Column(name = "base_url")
    private String baseUrl;

    /**
     * API key (encrypted) - for openai, gemini, claude
     */
    @Column(name = "api_key", length = 512)
    private String apiKey;

    /**
     * Model name to use
     */
    @Column
    private String model;

    /**
     * Request timeout in milliseconds
     */
    @Column(name = "timeout_ms")
    @Builder.Default
    private Long timeoutMs = 60000L;

    /**
     * Whether this config is active
     */
    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
