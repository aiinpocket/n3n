package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for generic action nodes.
 * Action nodes perform operations on data and pass it to the next node.
 */
@Component
@Slf4j
public class ActionNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "action";
    }

    @Override
    public String getDisplayName() {
        return "Action";
    }

    @Override
    public String getDescription() {
        return "Performs a configurable action on the input data.";
    }

    @Override
    public String getCategory() {
        return "Actions";
    }

    @Override
    public String getIcon() {
        return "bolt";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        Map<String, Object> inputData = context.getInputData();
        Map<String, Object> config = context.getNodeConfig();

        String actionType = getStringConfig(context, "actionType", "passthrough");

        Map<String, Object> output;

        switch (actionType) {
            case "transform":
                output = executeTransform(inputData, config);
                break;
            case "set":
                output = executeSet(inputData, config);
                break;
            case "merge":
                output = executeMerge(inputData, config);
                break;
            case "passthrough":
            default:
                output = inputData != null ? new HashMap<>(inputData) : new HashMap<>();
                break;
        }

        return NodeExecutionResult.success(output);
    }

    private Map<String, Object> executeTransform(Map<String, Object> input, Map<String, Object> config) {
        Map<String, Object> output = new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, String> mappings = (Map<String, String>) config.get("mappings");

        if (mappings != null && input != null) {
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                String targetKey = entry.getKey();
                String sourceKey = entry.getValue();
                if (input.containsKey(sourceKey)) {
                    output.put(targetKey, input.get(sourceKey));
                }
            }
        }

        return output;
    }

    private Map<String, Object> executeSet(Map<String, Object> input, Map<String, Object> config) {
        Map<String, Object> output = input != null ? new HashMap<>(input) : new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> values = (Map<String, Object>) config.get("values");

        if (values != null) {
            output.putAll(values);
        }

        return output;
    }

    private Map<String, Object> executeMerge(Map<String, Object> input, Map<String, Object> config) {
        Map<String, Object> output = input != null ? new HashMap<>(input) : new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> additionalData = (Map<String, Object>) config.get("additionalData");

        if (additionalData != null) {
            output.putAll(additionalData);
        }

        return output;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "actionType", Map.of(
                    "type", "string",
                    "title", "Action Type",
                    "enum", List.of("passthrough", "transform", "set", "merge"),
                    "default", "passthrough"
                ),
                "mappings", Map.of(
                    "type", "object",
                    "title", "Field Mappings",
                    "description", "Map source fields to target fields (for transform)"
                ),
                "values", Map.of(
                    "type", "object",
                    "title", "Values to Set",
                    "description", "Static values to add to output (for set)"
                ),
                "additionalData", Map.of(
                    "type", "object",
                    "title", "Additional Data",
                    "description", "Data to merge with input (for merge)"
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", true)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "any")
            )
        );
    }
}
