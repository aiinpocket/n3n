package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Handler for error trigger nodes.
 * Activates when another workflow encounters an error, allowing
 * error handling workflows to respond to failures in other flows.
 */
@Component
@Slf4j
public class ErrorTriggerHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "errorTrigger";
    }

    @Override
    public String getDisplayName() {
        return "Error Trigger";
    }

    @Override
    public String getDescription() {
        return "Triggers when another workflow encounters an error.";
    }

    @Override
    public String getCategory() {
        return "Triggers";
    }

    @Override
    public String getIcon() {
        return "warning";
    }

    @Override
    public boolean isTrigger() {
        return true;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        // Error trigger data comes from global context when an error occurs in another flow
        Map<String, Object> triggerInput = context.getGlobal("triggerInput", null);

        Map<String, Object> output = new HashMap<>();
        output.put("triggeredAt", Instant.now().toString());
        output.put("triggerType", "error");

        // Configuration
        String sourceFlowId = getStringConfig(context, "sourceFlowId", "");
        boolean retryEnabled = getBooleanConfig(context, "retryEnabled", false);

        if (triggerInput != null) {
            // Extract error details from the trigger input
            String errorMessage = extractString(triggerInput, "errorMessage", "Unknown error");
            String errorStack = extractString(triggerInput, "errorStack", "");
            String sourceNode = extractString(triggerInput, "sourceNode", "");
            String sourceFlowName = extractString(triggerInput, "sourceFlowName", "");
            String sourceFlowIdFromInput = extractString(triggerInput, "sourceFlowId", "");
            String sourceExecutionId = extractString(triggerInput, "sourceExecutionId", "");
            String errorType = extractString(triggerInput, "errorType", "runtime");

            // Filter by source flow if configured
            if (!sourceFlowId.isEmpty() && !sourceFlowId.equals(sourceFlowIdFromInput)) {
                log.debug("Error trigger skipped: source flow {} does not match filter {}",
                    sourceFlowIdFromInput, sourceFlowId);
                return NodeExecutionResult.failure(
                    "Error source flow does not match filter. Expected: " + sourceFlowId);
            }

            // Filter by error types if configured
            List<String> errorTypes = getErrorTypesConfig(context);
            if (!errorTypes.isEmpty() && !errorTypes.contains(errorType)) {
                log.debug("Error trigger skipped: error type {} not in filter {}", errorType, errorTypes);
                return NodeExecutionResult.failure(
                    "Error type '" + errorType + "' not in configured filter: " + errorTypes);
            }

            // Build error output
            Map<String, Object> errorInfo = new HashMap<>();
            errorInfo.put("message", errorMessage);
            errorInfo.put("stack", errorStack);
            errorInfo.put("type", errorType);

            Map<String, Object> sourceInfo = new HashMap<>();
            sourceInfo.put("flowId", sourceFlowIdFromInput);
            sourceInfo.put("flowName", sourceFlowName);
            sourceInfo.put("nodeId", sourceNode);
            sourceInfo.put("executionId", sourceExecutionId);

            output.put("error", errorInfo);
            output.put("source", sourceInfo);
            output.put("retryEnabled", retryEnabled);

            // Pass through any additional data from the trigger
            if (triggerInput.containsKey("nodeOutput")) {
                output.put("lastNodeOutput", triggerInput.get("nodeOutput"));
            }
        } else {
            // Return sample structure for testing / manual trigger
            Map<String, Object> sampleError = new HashMap<>();
            sampleError.put("message", "Sample error for testing");
            sampleError.put("stack", "");
            sampleError.put("type", "runtime");

            Map<String, Object> sampleSource = new HashMap<>();
            sampleSource.put("flowId", "");
            sampleSource.put("flowName", "");
            sampleSource.put("nodeId", "");
            sampleSource.put("executionId", "");

            output.put("error", sampleError);
            output.put("source", sampleSource);
            output.put("retryEnabled", retryEnabled);

            log.debug("Error trigger executed without trigger input (test mode)");
        }

        log.info("Error trigger activated: {}", output.get("error"));

        return NodeExecutionResult.success(output);
    }

    /**
     * Extract a string value from a map with a default fallback.
     */
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    /**
     * Get the configured error types filter list.
     */
    @SuppressWarnings("unchecked")
    private List<String> getErrorTypesConfig(NodeExecutionContext context) {
        Object value = context.getNodeConfig().get("errorTypes");
        if (value instanceof List) {
            return (List<String>) value;
        }
        return List.of();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("sourceFlowId", Map.of(
            "type", "string",
            "title", "Source Flow ID",
            "description", "Only trigger for errors from this specific flow (optional, leave empty for all flows)"
        ));

        properties.put("errorTypes", Map.of(
            "type", "array",
            "title", "Error Types",
            "description", "Only trigger for these error types (empty = all types)",
            "items", Map.of(
                "type", "string",
                "enum", List.of("runtime", "timeout", "configuration", "connection", "authentication", "validation")
            )
        ));

        properties.put("retryEnabled", Map.of(
            "type", "boolean",
            "title", "Enable Retry",
            "default", false,
            "description", "Whether the error handling workflow can retry the failed execution"
        ));

        return Map.of(
            "type", "object",
            "properties", properties
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(),  // Triggers have no inputs
            "outputs", List.of(
                Map.of("name", "output", "type", "object",
                    "description", "Error details including error message, stack trace, source node and flow info")
            )
        );
    }
}
