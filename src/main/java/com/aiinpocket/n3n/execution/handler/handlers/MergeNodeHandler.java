package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler for merge nodes.
 * Combines data from multiple input branches into one output.
 */
@Component
@Slf4j
public class MergeNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "merge";
    }

    @Override
    public String getDisplayName() {
        return "Merge";
    }

    @Override
    public String getDescription() {
        return "Combines data from multiple input branches into a single output.";
    }

    @Override
    public String getCategory() {
        return "Flow Control";
    }

    @Override
    public String getIcon() {
        return "merge";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        Map<String, Object> inputData = context.getInputData();
        String mode = getStringConfig(context, "mode", "append");
        String outputKey = getStringConfig(context, "outputKey", "merged");

        log.debug("Merging data with mode: {}", mode);

        Object result;

        switch (mode) {
            case "append":
                // Combine all inputs into a list
                result = combineToList(inputData);
                break;

            case "combine":
                // Merge all objects into one
                result = combineToObject(inputData);
                break;

            case "multiplex":
                // Wait for all inputs and output them together
                result = inputData;
                break;

            case "chooseBranch":
                // Choose first non-null input
                result = chooseFirstNonNull(inputData);
                break;

            default:
                result = inputData;
        }

        Map<String, Object> output = new HashMap<>();
        output.put(outputKey, result);

        return NodeExecutionResult.builder()
            .success(true)
            .output(output)
            .build();
    }

    @SuppressWarnings("unchecked")
    private List<Object> combineToList(Map<String, Object> inputData) {
        List<Object> result = new ArrayList<>();

        for (Object value : inputData.values()) {
            if (value instanceof List) {
                result.addAll((List<Object>) value);
            } else if (value != null) {
                result.add(value);
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> combineToObject(Map<String, Object> inputData) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : inputData.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                result.putAll((Map<String, Object>) value);
            } else if (value != null) {
                result.put(entry.getKey(), value);
            }
        }

        return result;
    }

    private Object chooseFirstNonNull(Map<String, Object> inputData) {
        return inputData.values().stream()
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "mode", Map.of(
                    "type", "string",
                    "title", "Merge Mode",
                    "description", "How to combine the inputs",
                    "enum", List.of("append", "combine", "multiplex", "chooseBranch"),
                    "enumNames", List.of(
                        "Append (combine as list)",
                        "Combine (merge objects)",
                        "Multiplex (output all)",
                        "Choose Branch (first non-null)"
                    ),
                    "default", "append"
                ),
                "outputKey", Map.of(
                    "type", "string",
                    "title", "Output Key",
                    "description", "Key name for merged result",
                    "default", "merged"
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input1", "type", "any", "required", true),
                Map.of("name", "input2", "type", "any", "required", false),
                Map.of("name", "input3", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "any")
            )
        );
    }
}
