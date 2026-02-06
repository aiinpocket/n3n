package com.aiinpocket.n3n.ai.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_token_usage")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiTokenUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID userId;

    private String provider;

    private String model;

    private int inputTokens;

    private int outputTokens;

    private UUID executionId;

    private String nodeId;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
