package com.aiinpocket.n3n.execution.service;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.entity.ApprovalAction;
import com.aiinpocket.n3n.execution.entity.Execution;
import com.aiinpocket.n3n.execution.entity.ExecutionApproval;
import com.aiinpocket.n3n.execution.repository.ApprovalActionRepository;
import com.aiinpocket.n3n.execution.repository.ExecutionApprovalRepository;
import com.aiinpocket.n3n.execution.repository.ExecutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionApprovalService {

    private final ExecutionApprovalRepository approvalRepository;
    private final ApprovalActionRepository actionRepository;
    private final ExecutionNotificationService notificationService;
    private final ExecutionRepository executionRepository;

    /**
     * Create an approval request for an execution.
     *
     * @param executionId The execution ID
     * @param nodeId The node ID requesting approval
     * @param message The message to display to approvers
     * @param requiredApprovers Number of approvers required
     * @param approvalMode Mode: "any", "all", or "majority"
     * @param expiresInMinutes Optional expiration time in minutes
     * @param metadata Additional metadata
     * @return The created approval
     */
    @Transactional
    public ExecutionApproval createApproval(UUID executionId, String nodeId, String message,
                                             Integer requiredApprovers, String approvalMode,
                                             Integer expiresInMinutes, Map<String, Object> metadata) {
        // Check if approval already exists
        Optional<ExecutionApproval> existing = approvalRepository.findByExecutionIdAndNodeId(executionId, nodeId);
        if (existing.isPresent()) {
            log.warn("Approval already exists for execution={}, node={}", executionId, nodeId);
            return existing.get();
        }

        ExecutionApproval approval = ExecutionApproval.builder()
            .executionId(executionId)
            .nodeId(nodeId)
            .approvalType("manual")
            .message(message)
            .requiredApprovers(requiredApprovers != null ? requiredApprovers : 1)
            .approvalMode(approvalMode != null ? approvalMode : "any")
            .metadata(metadata)
            .status("pending")
            .approvedCount(0)
            .rejectedCount(0)
            .expiresAt(expiresInMinutes != null && expiresInMinutes > 0
                ? Instant.now().plus(expiresInMinutes, ChronoUnit.MINUTES)
                : null)
            .build();

        approval = approvalRepository.save(approval);
        log.info("Created approval: id={}, executionId={}, nodeId={}", approval.getId(), executionId, nodeId);

        // Send notification
        notificationService.notifyApprovalCreated(executionId, nodeId, approval.getId(), message);

        return approval;
    }

    /**
     * Submit an approval action (approve or reject).
     *
     * @param approvalId The approval ID
     * @param userId The user submitting the action
     * @param action The action: "approve" or "reject"
     * @param comment Optional comment
     * @return The updated approval
     */
    @Transactional
    public ExecutionApproval submitApproval(UUID approvalId, UUID userId, String action, String comment) {
        ExecutionApproval approval = approvalRepository.findById(approvalId)
            .orElseThrow(() -> new ResourceNotFoundException("Approval not found: " + approvalId));

        if (approval.isResolved()) {
            throw new IllegalStateException("Approval has already been resolved: " + approval.getStatus());
        }

        if (approval.isExpired()) {
            approval.setStatus("expired");
            approval.setResolvedAt(Instant.now());
            approvalRepository.save(approval);
            throw new IllegalStateException("Approval has expired");
        }

        // Check if user has already acted
        if (actionRepository.existsByApprovalIdAndUserId(approvalId, userId)) {
            throw new IllegalStateException("User has already submitted an action for this approval");
        }

        // Create action record
        ApprovalAction approvalAction = ApprovalAction.builder()
            .approvalId(approvalId)
            .userId(userId)
            .action(action)
            .comment(comment)
            .build();
        actionRepository.save(approvalAction);

        // Update counts
        if ("approve".equals(action)) {
            approval.setApprovedCount(approval.getApprovedCount() + 1);
        } else if ("reject".equals(action)) {
            approval.setRejectedCount(approval.getRejectedCount() + 1);
        }

        // Send notification
        notificationService.notifyApprovalAction(approval.getExecutionId(), approvalId, action, userId);

        // Check if resolution threshold is met
        checkApprovalResolution(approval);

        return approvalRepository.save(approval);
    }

    /**
     * Check if the approval has met its resolution threshold and resolve if so.
     */
    private void checkApprovalResolution(ExecutionApproval approval) {
        if (approval.isApprovalMet()) {
            approval.setStatus("approved");
            approval.setResolvedAt(Instant.now());
            log.info("Approval approved: id={}, mode={}, approvedCount={}",
                approval.getId(), approval.getApprovalMode(), approval.getApprovedCount());
            notificationService.notifyApprovalResolved(approval.getExecutionId(), approval.getId(), "approved");
        } else if (approval.isRejectionMet()) {
            approval.setStatus("rejected");
            approval.setResolvedAt(Instant.now());
            log.info("Approval rejected: id={}, mode={}, rejectedCount={}",
                approval.getId(), approval.getApprovalMode(), approval.getRejectedCount());
            notificationService.notifyApprovalResolved(approval.getExecutionId(), approval.getId(), "rejected");
        }
    }

    /**
     * Get an approval by ID.
     */
    public ExecutionApproval getApproval(UUID approvalId) {
        return approvalRepository.findById(approvalId)
            .orElseThrow(() -> new ResourceNotFoundException("Approval not found: " + approvalId));
    }

    /**
     * Get approval for an execution and node.
     */
    public Optional<ExecutionApproval> getApprovalForExecution(UUID executionId, String nodeId) {
        return approvalRepository.findByExecutionIdAndNodeId(executionId, nodeId);
    }

    /**
     * Get pending approval for an execution.
     */
    public Optional<ExecutionApproval> getPendingApprovalForExecution(UUID executionId) {
        return approvalRepository.findPendingByExecutionId(executionId);
    }

    /**
     * Get all approvals for an execution.
     */
    public List<ExecutionApproval> getApprovalsForExecution(UUID executionId) {
        return approvalRepository.findByExecutionId(executionId);
    }

    /**
     * Get all pending approvals.
     */
    public List<ExecutionApproval> getAllPendingApprovals() {
        return approvalRepository.findAllPending();
    }

    /**
     * Check if a user is authorized to view/interact with an approval.
     * A user is authorized if they triggered the execution associated with the approval.
     */
    public boolean isUserAuthorizedForApproval(ExecutionApproval approval, UUID userId) {
        return executionRepository.findById(approval.getExecutionId())
            .map(execution -> userId.equals(execution.getTriggeredBy()))
            .orElse(false);
    }

    /**
     * Get actions for an approval.
     */
    public List<ApprovalAction> getActionsForApproval(UUID approvalId) {
        return actionRepository.findByApprovalIdOrderByCreatedAtDesc(approvalId);
    }

    /**
     * Expire old pending approvals.
     * This should be called periodically by a scheduled task.
     */
    @Transactional
    public int expireOldApprovals() {
        List<ExecutionApproval> expired = approvalRepository.findExpiredApprovals(Instant.now());
        for (ExecutionApproval approval : expired) {
            approval.setStatus("expired");
            approval.setResolvedAt(Instant.now());
            approvalRepository.save(approval);
            notificationService.notifyApprovalResolved(approval.getExecutionId(), approval.getId(), "expired");
            log.info("Expired approval: id={}", approval.getId());
        }
        return expired.size();
    }

    /**
     * Cancel an approval (e.g., when execution is cancelled).
     */
    @Transactional
    public void cancelApproval(UUID approvalId) {
        approvalRepository.findById(approvalId).ifPresent(approval -> {
            if (!approval.isResolved()) {
                approval.setStatus("cancelled");
                approval.setResolvedAt(Instant.now());
                approvalRepository.save(approval);
                notificationService.notifyApprovalResolved(approval.getExecutionId(), approval.getId(), "cancelled");
                log.info("Cancelled approval: id={}", approvalId);
            }
        });
    }
}
