package com.aiinpocket.n3n.execution.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing an individual approval or rejection action by a user.
 */
@Entity
@Table(name = "approval_actions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "approval_id", nullable = false)
    private UUID approvalId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "action", nullable = false, length = 20)
    private String action;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Check if this action is an approval
     */
    public boolean isApproval() {
        return "approve".equals(action);
    }

    /**
     * Check if this action is a rejection
     */
    public boolean isRejection() {
        return "reject".equals(action);
    }
}
