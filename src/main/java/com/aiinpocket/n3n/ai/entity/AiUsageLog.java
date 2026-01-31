package com.aiinpocket.n3n.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * AI 使用記錄實體
 */
@Entity
@Table(name = "ai_usage_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "config_id")
    private UUID configId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String model;

    @Column(name = "input_tokens")
    @Builder.Default
    private Integer inputTokens = 0;

    @Column(name = "output_tokens")
    @Builder.Default
    private Integer outputTokens = 0;

    @Column(name = "total_tokens")
    @Builder.Default
    private Integer totalTokens = 0;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(nullable = false)
    @Builder.Default
    private String status = "success";

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "request_type")
    @Builder.Default
    private String requestType = "chat";

    @Column(name = "execution_id")
    private UUID executionId;

    @Column(name = "node_id")
    private String nodeId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
