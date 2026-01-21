package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Handler for manual trigger nodes.
 * These nodes start workflow execution when triggered manually or via API.
 */
@Component
@Slf4j
public class TriggerNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "trigger";
    }

    @Override
    public String getDisplayName() {
        return "Manual Trigger";
    }

    @Override
    public String getDescription() {
        return "Starts the workflow execution. Can be triggered manually or via webhook.";
    }

    @Override
    public String getCategory() {
        return "Triggers";
    }

    @Override
    public String getIcon() {
        return "play";
    }

    @Override
    public boolean isTrigger() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        // Trigger nodes pass through their input data or initial payload
        Map<String, Object> inputData = context.getInputData();

        // If no input data, create empty output
        if (inputData == null || inputData.isEmpty()) {
            return NodeExecutionResult.success(createOutput(Map.of(
                "triggered", true,
                "timestamp", System.currentTimeMillis()
            )));
        }

        // Pass through the input data
        return NodeExecutionResult.success(inputData);
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "webhookEnabled", Map.of(
                    "type", "boolean",
                    "title", "Enable Webhook",
                    "default", false
                ),
                "webhookPath", Map.of(
                    "type", "string",
                    "title", "Webhook Path",
                    "description", "Custom path for webhook URL"
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(),  // Triggers have no inputs
            "outputs", List.of(
                Map.of("name", "output", "type", "any")
            )
        );
    }
}
