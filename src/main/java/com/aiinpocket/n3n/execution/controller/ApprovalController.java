package com.aiinpocket.n3n.execution.controller;

import com.aiinpocket.n3n.execution.entity.ExecutionApproval;
import com.aiinpocket.n3n.execution.service.ExecutionApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Global approval API for approval dashboard.
 */
@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Approvals", description = "Approval dashboard")
public class ApprovalController {

    private final ExecutionApprovalService approvalService;

    /**
     * Get all pending approvals.
     */
    @GetMapping("/pending")
    public ResponseEntity<List<ApprovalSummary>> getPendingApprovals(
            @AuthenticationPrincipal UserDetails userDetails) {
        // Return all pending approvals - user filtering happens at service layer
        List<ExecutionApproval> approvals = approvalService.getAllPendingApprovals();
        List<ApprovalSummary> summaries = approvals.stream()
            .map(ApprovalSummary::from)
            .toList();
        return ResponseEntity.ok(summaries);
    }

    /**
     * Get approval by ID.
     */
    @GetMapping("/{approvalId}")
    public ResponseEntity<ApprovalDetail> getApproval(@PathVariable UUID approvalId) {
        ExecutionApproval approval = approvalService.getApproval(approvalId);
        return ResponseEntity.ok(ApprovalDetail.from(approval,
            approvalService.getActionsForApproval(approvalId)));
    }

    /**
     * Submit approval action directly by approval ID.
     */
    @PostMapping("/{approvalId}")
    public ResponseEntity<ApprovalDetail> submitApproval(
            @PathVariable UUID approvalId,
            @Valid @RequestBody ApprovalActionRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = UUID.fromString(userDetails.getUsername());

        ExecutionApproval approval = approvalService.submitApproval(
            approvalId,
            userId,
            request.action(),
            request.comment()
        );

        log.info("Approval action submitted: approvalId={}, action={}, userId={}",
            approvalId, request.action(), userId);

        return ResponseEntity.ok(ApprovalDetail.from(approval,
            approvalService.getActionsForApproval(approvalId)));
    }

    // DTO records

    public record ApprovalActionRequest(String action, String comment) {}

    public record ApprovalSummary(
        String id,
        String executionId,
        String nodeId,
        String message,
        String approvalMode,
        int requiredApprovers,
        int approvedCount,
        int rejectedCount,
        String expiresAt,
        String createdAt
    ) {
        public static ApprovalSummary from(ExecutionApproval approval) {
            return new ApprovalSummary(
                approval.getId().toString(),
                approval.getExecutionId().toString(),
                approval.getNodeId(),
                approval.getMessage(),
                approval.getApprovalMode(),
                approval.getRequiredApprovers(),
                approval.getApprovedCount(),
                approval.getRejectedCount(),
                approval.getExpiresAt() != null ? approval.getExpiresAt().toString() : null,
                approval.getCreatedAt() != null ? approval.getCreatedAt().toString() : null
            );
        }
    }

    public record ApprovalDetail(
        String id,
        String executionId,
        String nodeId,
        String approvalType,
        String message,
        int requiredApprovers,
        String approvalMode,
        String status,
        int approvedCount,
        int rejectedCount,
        String expiresAt,
        String createdAt,
        String resolvedAt,
        Map<String, Object> metadata,
        List<ActionDetail> actions
    ) {
        public static ApprovalDetail from(ExecutionApproval approval,
                                           List<com.aiinpocket.n3n.execution.entity.ApprovalAction> actions) {
            return new ApprovalDetail(
                approval.getId().toString(),
                approval.getExecutionId().toString(),
                approval.getNodeId(),
                approval.getApprovalType(),
                approval.getMessage(),
                approval.getRequiredApprovers(),
                approval.getApprovalMode(),
                approval.getStatus(),
                approval.getApprovedCount(),
                approval.getRejectedCount(),
                approval.getExpiresAt() != null ? approval.getExpiresAt().toString() : null,
                approval.getCreatedAt() != null ? approval.getCreatedAt().toString() : null,
                approval.getResolvedAt() != null ? approval.getResolvedAt().toString() : null,
                approval.getMetadata(),
                actions.stream().map(ActionDetail::from).toList()
            );
        }
    }

    public record ActionDetail(
        String id,
        String userId,
        String action,
        String comment,
        String createdAt
    ) {
        public static ActionDetail from(com.aiinpocket.n3n.execution.entity.ApprovalAction action) {
            return new ActionDetail(
                action.getId().toString(),
                action.getUserId().toString(),
                action.getAction(),
                action.getComment(),
                action.getCreatedAt() != null ? action.getCreatedAt().toString() : null
            );
        }
    }
}
