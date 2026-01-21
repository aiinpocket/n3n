package com.aiinpocket.n3n.execution.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "execution_archives")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionArchive {

    @Id
    private UUID id;

    @Column(name = "flow_version_id", nullable = false)
    private UUID flowVersionId;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> output;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "triggered_by")
    private UUID triggeredBy;

    @Column(name = "trigger_type")
    private String triggerType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "node_executions", columnDefinition = "jsonb")
    private Map<String, Object> nodeExecutions;

    @Column(name = "archived_at")
    @Builder.Default
    private Instant archivedAt = Instant.now();
}
