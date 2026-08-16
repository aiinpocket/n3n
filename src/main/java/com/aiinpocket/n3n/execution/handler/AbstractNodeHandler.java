package com.aiinpocket.n3n.execution.handler;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for node handlers providing common functionality.
 */
@Slf4j
public abstract class AbstractNodeHandler implements NodeHandler {

    @Override
    public NodeExecutionResult execute(NodeExecutionContext context) {
        Instant startTime = Instant.now();

        try {
            log.debug("Executing node {} of type {}", context.getNodeId(), getType());

            // Validate configuration
            ValidationResult validation = validateConfig(context.getNodeConfig());
            if (!validation.isValid()) {
                return NodeExecutionResult.builder()
                    .success(false)
                    .errorMessage(describeValidationErrors(validation))
                    .executionTime(Duration.between(startTime, Instant.now()))
                    .build();
            }

            // Execute the actual node logic
            NodeExecutionResult result = doExecute(context);

            // Set execution time
            Duration executionTime = Duration.between(startTime, Instant.now());
            result.setExecutionTime(executionTime);

            log.debug("Node {} completed in {}ms, success={}",
                context.getNodeId(), executionTime.toMillis(), result.isSuccess());

            return result;

        } catch (Exception e) {
            log.error("Node {} execution failed: {}", context.getNodeId(), e.getMessage(), e);
            return NodeExecutionResult.builder()
                .success(false)
                .errorMessage(sanitizeErrorMessage(e.getMessage()))
                .errorStack(getStackTrace(e))
                .executionTime(Duration.between(startTime, Instant.now()))
                .build();
        }
    }

    /**
     * Subclasses implement this to provide actual node execution logic.
     */
    protected abstract NodeExecutionResult doExecute(NodeExecutionContext context);

    @Override
    public Map<String, Object> getConfigSchema() {
        // Default empty schema - subclasses should override
        return Map.of(
            "type", "object",
            "properties", Map.of()
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        // Default single input/output - subclasses should override
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "any")
            )
        );
    }

    /**
     * 設定不完整時給使用者看的說明。
     * 原本這裡直接串 List&lt;ValidationError&gt; 的 toString，畫面上會出現
     * 「Configuration validation failed: [ValidationResult.ValidationError(field=...)]」，
     * 沒有技術背景的人完全看不懂自己該補什麼。
     */
    private String describeValidationErrors(ValidationResult validation) {
        List<ValidationResult.ValidationError> errors = validation.getErrors();
        if (errors == null || errors.isEmpty()) {
            return "This step is missing required settings";
        }
        String details = errors.stream()
            .map(e -> e.getField() == null || e.getField().isBlank()
                ? e.getMessage()
                : e.getField() + " — " + e.getMessage())
            .collect(java.util.stream.Collectors.joining("; "));
        return "This step is missing required settings: " + details;
    }

    /**
     * Helper to get a string config value.
     */
    protected String getStringConfig(NodeExecutionContext context, String key, String defaultValue) {
        Object value = context.getNodeConfig().get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Helper to get an integer config value.
     */
    protected int getIntConfig(NodeExecutionContext context, String key, int defaultValue) {
        Object value = context.getNodeConfig().get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Helper to get a boolean config value.
     */
    protected boolean getBooleanConfig(NodeExecutionContext context, String key, boolean defaultValue) {
        Object value = context.getNodeConfig().get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    /**
     * Helper to get a double config value.
     */
    protected double getDoubleConfig(NodeExecutionContext context, String key, double defaultValue) {
        Object value = context.getNodeConfig().get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Helper to get a map config value.
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getMapConfig(NodeExecutionContext context, String key) {
        Object value = context.getNodeConfig().get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    /**
     * Helper to create a simple output map.
     */
    protected Map<String, Object> createOutput(Object data) {
        Map<String, Object> output = new HashMap<>();
        output.put("data", data);
        return output;
    }

    /**
     * Helper to create output with multiple fields.
     */
    protected Map<String, Object> createOutput(String key, Object value) {
        Map<String, Object> output = new HashMap<>();
        output.put(key, value);
        return output;
    }

    /**
     * Sanitize error messages to prevent leaking infrastructure details.
     * Strips IP addresses, JDBC URLs, MongoDB URIs, HTTP URLs, file paths, and truncates to maxLen.
     */
    protected static String sanitizeErrorMessage(String message, int maxLen) {
        if (message == null) return "Unknown error";
        String s = message;
        // Strip IP addresses
        s = s.replaceAll("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}(:\\d+)?", "***");
        // Strip JDBC URLs
        s = s.replaceAll("jdbc:[a-z]+://[^\\s,;)]+", "jdbc:***");
        // Strip MongoDB connection strings
        s = s.replaceAll("mongodb(\\+srv)?://[^\\s,;)]+", "mongodb://***");
        // Strip HTTP/HTTPS URLs
        s = s.replaceAll("https?://[^\\s,;)]+", "http://***");
        // Strip absolute file paths
        s = s.replaceAll("/(?:home|tmp|var|etc|usr|opt|Users|mnt)/[^\\s,;)]+", "/***");
        // Strip GCP project references
        s = s.replaceAll("projects/[^/\\s]+", "projects/***");
        // Strip Bearer/Basic auth tokens
        s = s.replaceAll("(?i)bearer\\s+[A-Za-z0-9._\\-]+", "bearer ***");
        s = s.replaceAll("(?i)basic\\s+[A-Za-z0-9+/=]+", "basic ***");
        // Strip key=value patterns for common secret fields
        s = s.replaceAll("(?i)(password|secret|api[_-]?key|token|authorization)\\s*[=:]\\s*[^\\s,;)\"']+", "$1=***");
        // Truncate
        if (s.length() > maxLen) {
            s = s.substring(0, maxLen) + "...";
        }
        return s;
    }

    /**
     * Sanitize error messages with default max length of 300.
     */
    protected static String sanitizeErrorMessage(String message) {
        return sanitizeErrorMessage(message, 300);
    }

    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
            if (sb.length() > 2000) {
                sb.append("... truncated");
                break;
            }
        }
        return sb.toString();
    }
}
