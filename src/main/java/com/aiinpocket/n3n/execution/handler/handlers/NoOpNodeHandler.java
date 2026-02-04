package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Handler for no-operation nodes.
 * Passes data through without modification - useful as placeholder.
 */
@Component
@Slf4j
public class NoOpNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "noOp";
    }

    @Override
    public String getDisplayName() {
        return "No Operation";
    }

    @Override
    public String getDescription() {
        return "Passes data through without modification. Useful as a placeholder or for debugging.";
    }

    @Override
    public String getCategory() {
        return "Flow Control";
    }

    @Override
    public String getIcon() {
        return "minus-circle";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        Map<String, Object> inputData = context.getInputData();
        boolean logData = getBooleanConfig(context, "logData", false);

        if (logData && inputData != null) {
            log.info("NoOp node passing through data: {}", inputData);
        } else {
            log.debug("NoOp node executing");
        }

        // Simply pass through the input data
        return NodeExecutionResult.builder()
            .success(true)
            .output(inputData != null ? inputData : Map.of())
            .build();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "logData", Map.of(
                    "type", "boolean",
                    "title", "Log Data",
                    "description", "Log the data passing through this node",
                    "default", false
                ),
                "note", Map.of(
                    "type", "string",
                    "title", "Note",
                    "description", "Optional note for documentation purposes"
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
