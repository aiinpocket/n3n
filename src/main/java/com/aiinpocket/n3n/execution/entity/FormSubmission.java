package com.aiinpocket.n3n.execution.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing form data submitted during flow execution.
 * Used by Form nodes to pause execution and wait for user input.
 */
@Entity
@Table(name = "form_submissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "node_id", nullable = false, length = 100)
    private String nodeId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> data;

    @Column(name = "submitted_by")
    private UUID submittedBy;

    @Column(name = "submitted_ip", length = 45)
    private String submittedIp;

    @Column(name = "submitted_at")
    @Builder.Default
    private Instant submittedAt = Instant.now();
}
