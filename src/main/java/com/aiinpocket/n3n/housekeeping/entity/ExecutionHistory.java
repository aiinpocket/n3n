package com.aiinpocket.n3n.housekeeping.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Archived execution record.
 */
@Entity
@Table(name = "executions_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionHistory {

    @Id
    private UUID id;

    @Column(name = "flow_version_id", nullable = false)
    private UUID flowVersionId;

    @Column(name = "flow_id")
    private UUID flowId;

    @Column(name = "flow_name")
    private String flowName;

    @Column(name = "flow_version")
    private String flowVersion;

    @Column(nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_input", columnDefinition = "jsonb")
    private Map<String, Object> triggerInput;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "trigger_context", columnDefinition = "jsonb")
    private Map<String, Object> triggerContext;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "triggered_by")
    private UUID triggeredBy;

    @Column(name = "triggered_by_email")
    private String triggeredByEmail;

    @Column(name = "trigger_type")
    private String triggerType;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "paused_at")
    private Instant pausedAt;

    @Column(name = "waiting_node_id")
    private String waitingNodeId;

    @Column(name = "pause_reason")
    private String pauseReason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resume_condition", columnDefinition = "jsonb")
    private Map<String, Object> resumeCondition;

    @Column(name = "retry_of")
    private UUID retryOf;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "max_retries")
    private Integer maxRetries;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "original_created_at")
    private Instant originalCreatedAt;
}
