package com.aiinpocket.n3n.execution.controller;

import com.aiinpocket.n3n.execution.dto.ExecutionResponse;
import com.aiinpocket.n3n.execution.entity.ApprovalAction;
import com.aiinpocket.n3n.execution.entity.ExecutionApproval;
import com.aiinpocket.n3n.execution.service.ExecutionApprovalService;
import com.aiinpocket.n3n.execution.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/executions/{executionId}")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Execution Approvals", description = "Execution approval management")
public class ExecutionApprovalController {

    private final ExecutionApprovalService approvalService;
    private final ExecutionService executionService;

    /**
     * Get approval information for an execution.
     */
    @GetMapping("/approval")
    public ResponseEntity<ApprovalResponse> getApproval(
            @PathVariable UUID executionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        executionService.verifyExecutionAccess(executionId, userId);
        return approvalService.getPendingApprovalForExecution(executionId)
            .map(approval -> ResponseEntity.ok(ApprovalResponse.from(approval,
                approvalService.getActionsForApproval(approval.getId()))))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all approvals for an execution.
     */
    @GetMapping("/approvals")
    public ResponseEntity<List<ApprovalResponse>> getApprovals(
            @PathVariable UUID executionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        executionService.verifyExecutionAccess(executionId, userId);
        List<ExecutionApproval> approvals = approvalService.getApprovalsForExecution(executionId);
        List<ApprovalResponse> responses = approvals.stream()
            .map(approval -> ApprovalResponse.from(approval,
                approvalService.getActionsForApproval(approval.getId())))
            .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Submit an approval action (approve or reject).
     */
    @PostMapping("/approval")
    public ResponseEntity<ApprovalResponse> submitApproval(
            @PathVariable UUID executionId,
            @Valid @RequestBody ApprovalRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = UUID.fromString(userDetails.getUsername());

        // Find the pending approval
        ExecutionApproval approval = approvalService.getPendingApprovalForExecution(executionId)
            .orElseThrow(() -> new IllegalStateException("No pending approval found for execution: " + executionId));

        // Submit the action
        approval = approvalService.submitApproval(
            approval.getId(),
            userId,
            request.getAction(),
            request.getComment()
        );

        log.info("Approval action submitted: executionId={}, action={}, userId={}",
            executionId, request.getAction(), userId);

        // If approval is resolved, auto-resume the execution
        if (approval.isResolved() && !"cancelled".equals(approval.getStatus())) {
            Map<String, Object> resumeData = new HashMap<>();
            resumeData.put("approvalId", approval.getId().toString());
            resumeData.put("approvalStatus", approval.getStatus());

            if ("approved".equals(approval.getStatus())) {
                resumeData.put("approvedBy", userId.toString());
            } else {
                resumeData.put("rejectedBy", userId.toString());
            }
            if (request.getComment() != null) {
                resumeData.put("comment", request.getComment());
            }

            log.info("Auto-resuming execution after approval: executionId={}, status={}",
                executionId, approval.getStatus());
            executionService.resumeExecution(executionId, resumeData, userId);
        }

        return ResponseEntity.ok(ApprovalResponse.from(approval,
            approvalService.getActionsForApproval(approval.getId())));
    }

    /**
     * Resume a waiting execution manually.
     */
    @PostMapping("/resume")
    public ResponseEntity<ExecutionResponse> resumeExecution(
            @PathVariable UUID executionId,
            @RequestBody(required = false) Map<String, Object> resumeData,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = UUID.fromString(userDetails.getUsername());

        log.info("Manual resume requested: executionId={}, userId={}", executionId, userId);

        ExecutionResponse response = executionService.resumeExecution(
            executionId,
            resumeData != null ? resumeData : Map.of(),
            userId
        );

        return ResponseEntity.ok(response);
    }

    // DTO classes

    public record ApprovalRequest(
        String action,
        String comment
    ) {
        public String getAction() { return action; }
        public String getComment() { return comment; }
    }

    public record ApprovalResponse(
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
        List<ActionResponse> actions
    ) {
        public static ApprovalResponse from(ExecutionApproval approval, List<ApprovalAction> actions) {
            return new ApprovalResponse(
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
                actions.stream().map(ActionResponse::from).toList()
            );
        }
    }

    public record ActionResponse(
        String id,
        String userId,
        String action,
        String comment,
        String createdAt
    ) {
        public static ActionResponse from(ApprovalAction action) {
            return new ActionResponse(
                action.getId().toString(),
                action.getUserId().toString(),
                action.getAction(),
                action.getComment(),
                action.getCreatedAt() != null ? action.getCreatedAt().toString() : null
            );
        }
    }
}
