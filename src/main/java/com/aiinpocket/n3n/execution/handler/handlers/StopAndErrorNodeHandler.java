package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Handler for stop and error nodes.
 * Stops workflow execution and throws an error.
 */
@Component
@Slf4j
public class StopAndErrorNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "stopAndError";
    }

    @Override
    public String getDisplayName() {
        return "Stop And Error";
    }

    @Override
    public String getDescription() {
        return "Stops workflow execution and throws an error with a custom message.";
    }

    @Override
    public String getCategory() {
        return "Flow Control";
    }

    @Override
    public String getIcon() {
        return "stop";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String errorMessage = getStringConfig(context, "errorMessage", "Workflow stopped");
        String errorCode = getStringConfig(context, "errorCode", "WORKFLOW_STOPPED");
        boolean includeInput = getBooleanConfig(context, "includeInput", false);

        log.info("Stop and error triggered: {} ({})", errorMessage, errorCode);

        // Build error details
        Map<String, Object> errorDetails = new java.util.HashMap<>();
        errorDetails.put("code", errorCode);
        errorDetails.put("message", errorMessage);
        errorDetails.put("nodeId", context.getNodeId());
        errorDetails.put("timestamp", java.time.Instant.now().toString());

        if (includeInput && context.getInputData() != null) {
            errorDetails.put("input", context.getInputData());
        }

        // Return failure with error message
        return NodeExecutionResult.builder()
            .success(false)
            .errorMessage(errorMessage)
            .output(errorDetails)
            .build();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("errorMessage"),
            "properties", Map.of(
                "errorMessage", Map.of(
                    "type", "string",
                    "title", "Error Message",
                    "description", "Custom error message to display",
                    "default", "Workflow stopped"
                ),
                "errorCode", Map.of(
                    "type", "string",
                    "title", "Error Code",
                    "description", "Error code for programmatic handling",
                    "default", "WORKFLOW_STOPPED"
                ),
                "includeInput", Map.of(
                    "type", "boolean",
                    "title", "Include Input Data",
                    "description", "Include input data in error details",
                    "default", false
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of()
        );
    }
}
