package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for output/end nodes.
 * Terminal nodes that mark the end of workflow execution.
 */
@Component
@Slf4j
public class OutputNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "output";
    }

    @Override
    public String getDisplayName() {
        return "Output";
    }

    @Override
    public String getDescription() {
        return "Marks the end of a workflow branch and outputs the final data.";
    }

    @Override
    public String getCategory() {
        return "Flow Control";
    }

    @Override
    public String getIcon() {
        return "flag";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        Map<String, Object> inputData = context.getInputData();
        String outputMode = getStringConfig(context, "outputMode", "all");

        Map<String, Object> output;

        switch (outputMode) {
            case "selected":
                output = selectFields(inputData, context);
                break;
            case "expression":
                output = evaluateOutputExpression(inputData, context);
                break;
            case "all":
            default:
                output = inputData != null ? new HashMap<>(inputData) : new HashMap<>();
                break;
        }

        // Add output metadata if requested
        if (getBooleanConfig(context, "includeMetadata", false)) {
            output.put("_metadata", Map.of(
                "executionId", context.getExecutionId().toString(),
                "nodeId", context.getNodeId(),
                "timestamp", System.currentTimeMillis()
            ));
        }

        log.debug("Output node producing result with {} fields", output.size());

        return NodeExecutionResult.success(output);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> selectFields(Map<String, Object> input, NodeExecutionContext context) {
        Map<String, Object> output = new HashMap<>();

        Object fieldsConfig = context.getNodeConfig().get("selectedFields");
        if (fieldsConfig instanceof List) {
            List<String> fields = (List<String>) fieldsConfig;
            for (String field : fields) {
                if (input != null && input.containsKey(field)) {
                    output.put(field, input.get(field));
                }
            }
        }

        return output;
    }

    private Map<String, Object> evaluateOutputExpression(Map<String, Object> input, NodeExecutionContext context) {
        String expression = getStringConfig(context, "outputExpression", "");

        if (expression.isEmpty()) {
            return input != null ? new HashMap<>(input) : new HashMap<>();
        }

        ExpressionEvaluator evaluator = context.getExpressionEvaluator();
        if (evaluator != null && evaluator.containsExpression(expression)) {
            Object result = evaluator.evaluate(expression, context);
            if (result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapResult = (Map<String, Object>) result;
                return new HashMap<>(mapResult);
            } else {
                return Map.of("result", result);
            }
        }

        return input != null ? new HashMap<>(input) : new HashMap<>();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "outputMode", Map.of(
                    "type", "string",
                    "title", "Output Mode",
                    "enum", List.of("all", "selected", "expression"),
                    "default", "all",
                    "description", "How to determine the output data"
                ),
                "selectedFields", Map.of(
                    "type", "array",
                    "title", "Selected Fields",
                    "items", Map.of("type", "string"),
                    "description", "Fields to include in output (for 'selected' mode)"
                ),
                "outputExpression", Map.of(
                    "type", "string",
                    "title", "Output Expression",
                    "description", "Expression to evaluate for output (for 'expression' mode)"
                ),
                "includeMetadata", Map.of(
                    "type", "boolean",
                    "title", "Include Metadata",
                    "default", false,
                    "description", "Add execution metadata to output"
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
            "outputs", List.of()  // Output nodes have no outputs
        );
    }
}
