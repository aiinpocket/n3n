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

    // Extended fields from joins
    private String flowName;
    private String flowVersion;

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
            .build();
    }

    public static ExecutionResponse from(Execution e, String flowName, String flowVersion) {
        ExecutionResponse resp = from(e);
        resp.setFlowName(flowName);
        resp.setFlowVersion(flowVersion);
        return resp;
    }
}
