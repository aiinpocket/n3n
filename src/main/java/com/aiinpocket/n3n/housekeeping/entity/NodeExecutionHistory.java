package com.aiinpocket.n3n.housekeeping.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Archived node execution record.
 */
@Entity
@Table(name = "node_executions_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeExecutionHistory {

    @Id
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
    private String status;

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
    private Integer retryCount;

    @Column(name = "waiting_for")
    private String waitingFor;

    @Column(name = "waiting_since")
    private Instant waitingSince;

    @Column(name = "archived_at")
    private Instant archivedAt;
}
