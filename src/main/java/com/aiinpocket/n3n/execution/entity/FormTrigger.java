package com.aiinpocket.n3n.execution.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing a Form Trigger configuration.
 * Stores the form schema and generates a unique token for the public form URL.
 */
@Entity
@Table(name = "form_triggers")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormTrigger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flow_id", nullable = false)
    private UUID flowId;

    @Column(name = "node_id", nullable = false, length = 100)
    private String nodeId;

    @Column(name = "form_token", nullable = false, unique = true, length = 64)
    private String formToken;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> config;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "max_submissions")
    @Builder.Default
    private Integer maxSubmissions = 0;

    @Column(name = "submission_count")
    @Builder.Default
    private Integer submissionCount = 0;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * Check if this form trigger is expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if this form has reached its submission limit
     */
    public boolean isAtSubmissionLimit() {
        return maxSubmissions > 0 && submissionCount >= maxSubmissions;
    }

    /**
     * Check if this form can accept submissions
     */
    public boolean canAcceptSubmission() {
        return isActive && !isExpired() && !isAtSubmissionLimit();
    }

    /**
     * Increment submission count
     */
    public void incrementSubmissionCount() {
        this.submissionCount = (this.submissionCount == null ? 0 : this.submissionCount) + 1;
        this.updatedAt = Instant.now();
    }
}
