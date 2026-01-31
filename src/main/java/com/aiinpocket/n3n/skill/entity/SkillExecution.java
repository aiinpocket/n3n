package com.aiinpocket.n3n.skill.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Record of skill execution for auditing and debugging.
 */
@Entity
@Table(name = "skill_executions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "skill_id")
    private UUID skillId;

    @Column(name = "skill_name", nullable = false)
    private String skillName;

    @Column(name = "execution_id")
    private UUID executionId;

    @Column(name = "node_execution_id")
    private UUID nodeExecutionId;

    @Column(name = "input_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> inputData;

    @Column(name = "output_data", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> outputData;

    @Column(nullable = false)
    private String status;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "executed_by")
    private UUID executedBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
