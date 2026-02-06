package com.aiinpocket.n3n.logging;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class LogEntry {
    private Instant timestamp;
    private String level;       // INFO, WARN, ERROR, DEBUG
    private String logger;      // shortened logger name
    private String message;
    private String traceId;
    private String executionId;
    private String flowId;
    private String nodeId;
    private String userId;
    private String threadName;
}
