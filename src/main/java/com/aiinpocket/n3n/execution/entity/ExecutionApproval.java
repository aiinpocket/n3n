package com.aiinpocket.n3n.execution.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Entity representing an approval request for a waiting execution.
 * Supports various approval modes: any (first approver wins), all (unanimous),
 * and majority (>50% required).
 */
@Entity
@Table(name = "execution_approvals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "execution_id", nullable = false)
    private UUID executionId;

    @Column(name = "node_id", nullable = false, length = 100)
    private String nodeId;

    @Column(name = "approval_type", nullable = false, length = 50)
    @Builder.Default
    private String approvalType = "manual";

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "required_approvers", nullable = false)
    @Builder.Default
    private Integer requiredApprovers = 1;

    @Column(name = "approval_mode", nullable = false, length = 20)
    @Builder.Default
    private String approvalMode = "any";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "pending";

    @Column(name = "approved_count", nullable = false)
    @Builder.Default
    private Integer approvedCount = 0;

    @Column(name = "rejected_count", nullable = false)
    @Builder.Default
    private Integer rejectedCount = 0;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    /**
     * Check if this approval has been resolved (approved, rejected, expired, or cancelled)
     */
    public boolean isResolved() {
        return !"pending".equals(status);
    }

    /**
     * Check if this approval has expired
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if approval threshold is met based on approval mode
     */
    public boolean isApprovalMet() {
        return switch (approvalMode) {
            case "any" -> approvedCount >= 1;
            case "all" -> approvedCount >= requiredApprovers;
            case "majority" -> approvedCount > requiredApprovers / 2;
            default -> false;
        };
    }

    /**
     * Check if rejection threshold is met based on approval mode
     */
    public boolean isRejectionMet() {
        return switch (approvalMode) {
            case "any" -> rejectedCount >= 1;
            case "all" -> rejectedCount >= 1; // Any rejection fails "all" mode
            case "majority" -> rejectedCount > requiredApprovers / 2;
            default -> false;
        };
    }
}
