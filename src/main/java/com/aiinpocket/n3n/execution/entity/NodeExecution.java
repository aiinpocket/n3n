package com.aiinpocket.n3n.execution.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "node_executions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "node_id", nullable = false)
    private String nodeId;

    @Column(name = "component_name", nullable = false)
    private String componentName;

    @Column(name = "component_version", nullable = false)
    private String componentVersion;

    @Column(nullable = false)
    @Builder.Default
    private String status = "pending";

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "error_stack")
    private String errorStack;

    @Column(name = "worker_id")
    private String workerId;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;
}
