package com.aiinpocket.n3n.execution.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    @Data
    @Builder
    public static class ExecutionEvent {
        private String type;
        private UUID executionId;
        private String status;
        private String nodeId;
        private Map<String, Object> data;
        private Instant timestamp;
    }

    public void notifyExecutionStarted(UUID executionId, UUID flowVersionId) {
        ExecutionEvent event = ExecutionEvent.builder()
            .type("EXECUTION_STARTED")
            .executionId(executionId)
            .status("running")
            .data(Map.of("flowVersionId", flowVersionId.toString()))
            .timestamp(Instant.now())
            .build();

        sendToExecution(executionId, event);
        log.debug("Sent execution started notification: {}", executionId);
    }

    public void notifyNodeStarted(UUID executionId, String nodeId) {
        ExecutionEvent event = ExecutionEvent.builder()
            .type("NODE_STARTED")
            .executionId(executionId)
            .status("running")
            .nodeId(nodeId)
            .timestamp(Instant.now())
            .build();

        sendToExecution(executionId, event);
        log.debug("Sent node started notification: execution={}, node={}", executionId, nodeId);
    }

    public void notifyNodeCompleted(UUID executionId, String nodeId, Map<String, Object> output) {
        ExecutionEvent event = ExecutionEvent.builder()
            .type("NODE_COMPLETED")
            .executionId(executionId)
            .status("completed")
            .nodeId(nodeId)
            .data(output)
            .timestamp(Instant.now())
            .build();

        sendToExecution(executionId, event);
        log.debug("Sent node completed notification: execution={}, node={}", executionId, nodeId);
    }

    public void notifyNodeFailed(UUID executionId, String nodeId, String errorMessage) {
        ExecutionEvent event = ExecutionEvent.builder()
            .type("NODE_FAILED")
            .executionId(executionId)
            .status("failed")
            .nodeId(nodeId)
            .data(Map.of("error", errorMessage))
            .timestamp(Instant.now())
            .build();

        sendToExecution(executionId, event);
        log.debug("Sent node failed notification: execution={}, node={}", executionId, nodeId);
    }

    public void notifyExecutionCompleted(UUID executionId, Map<String, Object> output) {
        ExecutionEvent event = ExecutionEvent.builder()
            .type("EXECUTION_COMPLETED")
            .executionId(executionId)
            .status("completed")
            .data(output)
            .timestamp(Instant.now())
            .build();

        sendToExecution(executionId, event);
        log.debug("Sent execution completed notification: {}", executionId);
    }

    public void notifyExecutionFailed(UUID executionId, String errorMessage) {
        ExecutionEvent event = ExecutionEvent.builder()
            .type("EXECUTION_FAILED")
            .executionId(executionId)
            .status("failed")
            .data(Map.of("error", errorMessage))
            .timestamp(Instant.now())
            .build();

        sendToExecution(executionId, event);
        log.debug("Sent execution failed notification: {}", executionId);
    }

    public void notifyExecutionCancelled(UUID executionId, String reason) {
        ExecutionEvent event = ExecutionEvent.builder()
            .type("EXECUTION_CANCELLED")
            .executionId(executionId)
            .status("cancelled")
            .data(reason != null ? Map.of("reason", reason) : Map.of())
            .timestamp(Instant.now())
            .build();

        sendToExecution(executionId, event);
        log.debug("Sent execution cancelled notification: {}", executionId);
    }

    /**
     * Notify that execution is waiting for external input (approval, form, etc.)
     */
    public void notifyExecutionWaiting(UUID executionId, String nodeId, String reason, Map<String, Object> resumeCondition) {
        ExecutionEvent event = ExecutionEvent.builder()
            .type("EXECUTION_WAITING")
            .executionId(executionId)
            .status("waiting")
            .nodeId(nodeId)
            .data(Map.of(
                "reason", reason != null ? reason : "Waiting for external input",
                "resumeCondition", resumeCondition != null ? resumeCondition : Map.of()
            ))
            .timestamp(Instant.now())
            .build();

        sendToExecution(executionId, event);
        log.debug("Sent execution waiting notification: execution={}, node={}", executionId, nodeId);
    }

    /**
     * Notify that execution has resumed from waiting state
     */
    public void notifyExecutionResumed(UUID executionId, String nodeId) {
        ExecutionEvent event = ExecutionEvent.builder()
            .type("EXECUTION_RESUMED")
            .executionId(executionId)
            .status("running")
            .nodeId(nodeId)
            .data(Map.of("resumedFrom", nodeId != null ? nodeId : "unknown"))
            .timestamp(Instant.now())
            .build();

        sendToExecution(executionId, event);
        log.debug("Sent execution resumed notification: execution={}, node={}", executionId, nodeId);
    }

    /**
     * Notify that a node is waiting for external input
     */
    public void notifyNodeWaiting(UUID executionId, String nodeId, String reason) {
        ExecutionEvent event = ExecutionEvent.builder()
            .type("NODE_WAITING")
            .executionId(executionId)
            .status("waiting")
            .nodeId(nodeId)
            .data(Map.of("reason", reason != null ? reason : "Waiting for input"))
            .timestamp(Instant.now())
            .build();

        sendToExecution(executionId, event);
        log.debug("Sent node waiting notification: execution={}, node={}", executionId, nodeId);
    }

    /**
     * Notify that an approval request has been created
     */
    public void notifyApprovalCreated(UUID executionId, String nodeId, UUID approvalId, String message) {
        ExecutionEvent event = ExecutionEvent.builder()
            .type("APPROVAL_CREATED")
            .executionId(executionId)
            .status("waiting")
            .nodeId(nodeId)
            .data(Map.of(
                "approvalId", approvalId.toString(),
                "message", message != null ? message : "Approval required"
            ))
            .timestamp(Instant.now())
            .build();

        sendToExecution(executionId, event);
        // Also send to approvals topic for approval dashboard
        messagingTemplate.convertAndSend("/topic/approvals", event);
        log.debug("Sent approval created notification: execution={}, approval={}", executionId, approvalId);
    }

    /**
     * Notify that an approval action has been submitted
     */
    public void notifyApprovalAction(UUID executionId, UUID approvalId, String action, UUID userId) {
        ExecutionEvent event = ExecutionEvent.builder()
            .type("APPROVAL_ACTION")
            .executionId(executionId)
            .data(Map.of(
                "approvalId", approvalId.toString(),
                "action", action,
                "userId", userId.toString()
            ))
            .timestamp(Instant.now())
            .build();

        sendToExecution(executionId, event);
        messagingTemplate.convertAndSend("/topic/approvals", event);
        log.debug("Sent approval action notification: execution={}, action={}", executionId, action);
    }

    /**
     * Notify that an approval has been resolved (approved/rejected/expired)
     */
    public void notifyApprovalResolved(UUID executionId, UUID approvalId, String resolution) {
        ExecutionEvent event = ExecutionEvent.builder()
            .type("APPROVAL_RESOLVED")
            .executionId(executionId)
            .data(Map.of(
                "approvalId", approvalId.toString(),
                "resolution", resolution
            ))
            .timestamp(Instant.now())
            .build();

        sendToExecution(executionId, event);
        messagingTemplate.convertAndSend("/topic/approvals", event);
        log.debug("Sent approval resolved notification: execution={}, resolution={}", executionId, resolution);
    }

    private void sendToExecution(UUID executionId, ExecutionEvent event) {
        // Send to execution-specific topic
        messagingTemplate.convertAndSend("/topic/executions/" + executionId, event);
        // Also send to global executions topic for dashboard updates
        messagingTemplate.convertAndSend("/topic/executions", event);
    }
}
