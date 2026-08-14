package com.aiinpocket.n3n.execution.dto;

import com.aiinpocket.n3n.execution.entity.Execution;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class ExecutionResponse {
    private UUID id;
    private UUID flowVersionId;
    private String status;
    private Map<String, Object> triggerInput;
    private Map<String, Object> triggerContext;
    private Instant startedAt;
    private Instant completedAt;
    private Integer durationMs;
    private UUID triggeredBy;
    private String triggerType;
    private String cancelReason;
    private UUID cancelledBy;
    private Instant cancelledAt;

    // Retry information
    private UUID retryOf;
    private Integer retryCount;
    private Integer maxRetries;
    private boolean canRetry;

    // Pause/resume fields
    private Instant pausedAt;
    private String waitingNodeId;
    private String pauseReason;
    private Map<String, Object> resumeCondition;

    // Extended fields from joins
    private UUID flowId;
    private String flowName;
    private String flowVersion;

    /** 設為永久：不受保留天數清理 */
    private boolean pinned;

    public static ExecutionResponse from(Execution e) {
        int retryCount = e.getRetryCount() != null ? e.getRetryCount() : 0;
        int maxRetries = e.getMaxRetries() != null ? e.getMaxRetries() : 3;
        boolean canRetry = ("failed".equals(e.getStatus()) || "cancelled".equals(e.getStatus()))
            && retryCount < maxRetries;

        return ExecutionResponse.builder()
            .id(e.getId())
            .flowVersionId(e.getFlowVersionId())
            .status(e.getStatus())
            .triggerInput(e.getTriggerInput())
            .triggerContext(e.getTriggerContext())
            .startedAt(e.getStartedAt())
            .completedAt(e.getCompletedAt())
            .durationMs(e.getDurationMs())
            .triggeredBy(e.getTriggeredBy())
            .triggerType(e.getTriggerType())
            .cancelReason(e.getCancelReason())
            .cancelledBy(e.getCancelledBy())
            .cancelledAt(e.getCancelledAt())
            .retryOf(e.getRetryOf())
            .retryCount(retryCount)
            .maxRetries(maxRetries)
            .canRetry(canRetry)
            .pausedAt(e.getPausedAt())
            .waitingNodeId(e.getWaitingNodeId())
            .pauseReason(e.getPauseReason())
            .resumeCondition(e.getResumeCondition())
            .pinned(Boolean.TRUE.equals(e.getPinned()))
            .build();
    }

    public static ExecutionResponse from(Execution e, UUID flowId, String flowName, String flowVersion) {
        ExecutionResponse resp = from(e);
        resp.setFlowId(flowId);
        resp.setFlowName(flowName);
        resp.setFlowVersion(flowVersion);
        return resp;
    }
}
