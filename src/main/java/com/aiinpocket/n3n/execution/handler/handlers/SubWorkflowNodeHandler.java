package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Handler for sub-workflow nodes.
 * Executes another workflow as part of the current flow.
 */
@Component
@Slf4j
public class SubWorkflowNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "subWorkflow";
    }

    @Override
    public String getDisplayName() {
        return "Execute Sub-workflow";
    }

    @Override
    public String getDescription() {
        return "Executes another workflow and returns its output.";
    }

    @Override
    public String getCategory() {
        return "Flow Control";
    }

    @Override
    public String getIcon() {
        return "workflow";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        Map<String, Object> inputData = context.getInputData();
        String workflowId = getStringConfig(context, "workflowId", "");
        String workflowVersion = getStringConfig(context, "workflowVersion", "latest");
        boolean waitForCompletion = getBooleanConfig(context, "waitForCompletion", true);

        if (workflowId.isEmpty()) {
            return NodeExecutionResult.builder()
                .success(false)
                .errorMessage("Workflow ID is required")
                .build();
        }

        log.info("Executing sub-workflow: {} (version: {})", workflowId, workflowVersion);

        // Note: In a real implementation, this would call ExecutionService to run the sub-workflow
        // For now, we return a placeholder indicating the sub-workflow should be triggered

        Map<String, Object> output = new HashMap<>();
        output.put("subWorkflowId", workflowId);
        output.put("subWorkflowVersion", workflowVersion);
        output.put("status", "triggered");
        output.put("inputData", inputData);
        output.put("waitForCompletion", waitForCompletion);

        // The actual sub-workflow execution would be handled by ExecutionService
        // This handler just prepares the execution request

        return NodeExecutionResult.builder()
            .success(true)
            .output(output)
            .metadata(Map.of(
                "subWorkflowId", workflowId,
                "subWorkflowVersion", workflowVersion
            ))
            .build();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("workflowId"),
            "properties", Map.of(
                "workflowId", Map.of(
                    "type", "string",
                    "title", "Workflow ID",
                    "description", "ID of the workflow to execute"
                ),
                "workflowVersion", Map.of(
                    "type", "string",
                    "title", "Workflow Version",
                    "description", "Version of the workflow (or 'latest')",
                    "default", "latest"
                ),
                "waitForCompletion", Map.of(
                    "type", "boolean",
                    "title", "Wait for Completion",
                    "description", "Wait for sub-workflow to complete before continuing",
                    "default", true
                ),
                "inputMapping", Map.of(
                    "type", "object",
                    "title", "Input Mapping",
                    "description", "Map input fields to sub-workflow parameters"
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
            "outputs", List.of(
                Map.of("name", "output", "type", "any")
            )
        );
    }
}
