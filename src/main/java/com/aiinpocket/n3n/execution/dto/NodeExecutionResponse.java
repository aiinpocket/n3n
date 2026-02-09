package com.aiinpocket.n3n.execution.dto;

import com.aiinpocket.n3n.execution.entity.NodeExecution;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class NodeExecutionResponse {
    private UUID id;
    private UUID executionId;
    private String nodeId;
    private String componentName;
    private String componentVersion;
    private String status;
    private Instant startedAt;
    private Instant completedAt;
    private Integer durationMs;
    private String errorMessage;
    private Integer retryCount;

    public static NodeExecutionResponse from(NodeExecution n) {
        return NodeExecutionResponse.builder()
            .id(n.getId())
            .executionId(n.getExecutionId())
            .nodeId(n.getNodeId())
            .componentName(n.getComponentName())
            .componentVersion(n.getComponentVersion())
            .status(n.getStatus())
            .startedAt(n.getStartedAt())
            .completedAt(n.getCompletedAt())
            .durationMs(n.getDurationMs())
            .errorMessage(n.getErrorMessage())
            .retryCount(n.getRetryCount())
            .build();
    }
}
