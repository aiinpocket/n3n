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
                    .errorMessage("Configuration validation failed: " + validation.getErrors())
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
                .errorMessage(e.getMessage())
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
