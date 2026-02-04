package com.aiinpocket.n3n.common.logging;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * Utility class for managing logging context (MDC).
 * Provides structured context for ELK integration.
 */
public final class LogContext {

    private LogContext() {}

    // MDC Keys - use consistent naming for ELK queries
    public static final String TRACE_ID = "traceId";
    public static final String EXECUTION_ID = "executionId";
    public static final String FLOW_ID = "flowId";
    public static final String NODE_ID = "nodeId";
    public static final String USER_ID = "userId";
    public static final String REQUEST_ID = "requestId";
    public static final String OPERATION = "operation";

    /**
     * Set execution context for flow execution logging.
     */
    public static void setExecutionContext(UUID executionId, UUID flowId, String nodeId) {
        if (executionId != null) {
            MDC.put(EXECUTION_ID, executionId.toString());
        }
        if (flowId != null) {
            MDC.put(FLOW_ID, flowId.toString());
        }
        if (nodeId != null) {
            MDC.put(NODE_ID, nodeId);
        }
    }

    /**
     * Set user context for request logging.
     */
    public static void setUserContext(UUID userId) {
        if (userId != null) {
            MDC.put(USER_ID, userId.toString());
        }
    }

    /**
     * Set trace ID for distributed tracing.
     */
    public static void setTraceId(String traceId) {
        if (traceId != null) {
            MDC.put(TRACE_ID, traceId);
        }
    }

    /**
     * Generate and set a new trace ID.
     */
    public static String generateTraceId() {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        MDC.put(TRACE_ID, traceId);
        return traceId;
    }

    /**
     * Set request ID for HTTP request tracking.
     */
    public static void setRequestId(String requestId) {
        if (requestId != null) {
            MDC.put(REQUEST_ID, requestId);
        }
    }

    /**
     * Set operation name for grouping related logs.
     */
    public static void setOperation(String operation) {
        if (operation != null) {
            MDC.put(OPERATION, operation);
        }
    }

    /**
     * Clear all context - call at the end of request/execution.
     */
    public static void clear() {
        MDC.clear();
    }

    /**
     * Clear execution-specific context but keep request context.
     */
    public static void clearExecutionContext() {
        MDC.remove(EXECUTION_ID);
        MDC.remove(FLOW_ID);
        MDC.remove(NODE_ID);
    }

    /**
     * Execute with execution context and automatically clean up.
     */
    public static void withExecutionContext(UUID executionId, UUID flowId, String nodeId, Runnable action) {
        try {
            setExecutionContext(executionId, flowId, nodeId);
            action.run();
        } finally {
            clearExecutionContext();
        }
    }

    /**
     * Get current trace ID.
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID);
    }

    /**
     * Get current execution ID.
     */
    public static String getExecutionId() {
        return MDC.get(EXECUTION_ID);
    }
}
