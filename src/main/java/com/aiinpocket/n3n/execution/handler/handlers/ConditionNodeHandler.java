package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Handler for condition/branch nodes.
 * Evaluates conditions and routes data to different branches.
 */
@Component
@Slf4j
public class ConditionNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "condition";
    }

    @Override
    public String getDisplayName() {
        return "Condition";
    }

    @Override
    public String getDescription() {
        return "Evaluates conditions and routes data to true or false branches.";
    }

    @Override
    public String getCategory() {
        return "Flow Control";
    }

    @Override
    public String getIcon() {
        return "git-branch";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        Map<String, Object> inputData = context.getInputData();

        String field = getStringConfig(context, "field", "");
        String operator = getStringConfig(context, "operator", "equals");
        Object compareValue = context.getNodeConfig().get("value");

        Object fieldValue = inputData != null ? getNestedValue(inputData, field) : null;

        boolean result = evaluateCondition(fieldValue, operator, compareValue);

        log.debug("Condition evaluated: {} {} {} = {}", field, operator, compareValue, result);

        // Return result with branch information
        String branch = result ? "true" : "false";

        return NodeExecutionResult.builder()
            .success(true)
            .output(inputData)
            .branchesToFollow(List.of(branch))
            .build();
    }

    private Object getNestedValue(Map<String, Object> data, String path) {
        if (path == null || path.isEmpty()) {
            return data;
        }

        String[] parts = path.split("\\.");
        Object current = data;

        for (String part : parts) {
            if (current instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) current;
                current = map.get(part);
            } else {
                return null;
            }
        }

        return current;
    }

    private boolean evaluateCondition(Object fieldValue, String operator, Object compareValue) {
        if (fieldValue == null && compareValue == null) {
            return "equals".equals(operator) || "isEmpty".equals(operator);
        }

        if (fieldValue == null) {
            return "notEquals".equals(operator) || "isEmpty".equals(operator);
        }

        switch (operator) {
            case "equals":
                return Objects.equals(fieldValue.toString(),
                    compareValue != null ? compareValue.toString() : null);

            case "notEquals":
                return !Objects.equals(fieldValue.toString(),
                    compareValue != null ? compareValue.toString() : null);

            case "contains":
                return compareValue != null &&
                    fieldValue.toString().contains(compareValue.toString());

            case "notContains":
                return compareValue == null ||
                    !fieldValue.toString().contains(compareValue.toString());

            case "startsWith":
                return compareValue != null &&
                    fieldValue.toString().startsWith(compareValue.toString());

            case "endsWith":
                return compareValue != null &&
                    fieldValue.toString().endsWith(compareValue.toString());

            case "greaterThan":
                return compareNumbers(fieldValue, compareValue) > 0;

            case "lessThan":
                return compareNumbers(fieldValue, compareValue) < 0;

            case "greaterOrEqual":
                return compareNumbers(fieldValue, compareValue) >= 0;

            case "lessOrEqual":
                return compareNumbers(fieldValue, compareValue) <= 0;

            case "isEmpty":
                return fieldValue.toString().isEmpty();

            case "isNotEmpty":
                return !fieldValue.toString().isEmpty();

            case "isTrue":
                return Boolean.parseBoolean(fieldValue.toString());

            case "isFalse":
                return !Boolean.parseBoolean(fieldValue.toString());

            default:
                log.warn("Unknown operator: {}, defaulting to false", operator);
                return false;
        }
    }

    private int compareNumbers(Object a, Object b) {
        try {
            double numA = Double.parseDouble(a.toString());
            double numB = b != null ? Double.parseDouble(b.toString()) : 0;
            return Double.compare(numA, numB);
        } catch (NumberFormatException e) {
            // Fall back to string comparison
            return a.toString().compareTo(b != null ? b.toString() : "");
        }
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("field", "operator"),
            "properties", Map.of(
                "field", Map.of(
                    "type", "string",
                    "title", "Field",
                    "description", "Field path to evaluate (e.g., data.status)"
                ),
                "operator", Map.of(
                    "type", "string",
                    "title", "Operator",
                    "enum", List.of(
                        "equals", "notEquals", "contains", "notContains",
                        "startsWith", "endsWith", "greaterThan", "lessThan",
                        "greaterOrEqual", "lessOrEqual", "isEmpty", "isNotEmpty",
                        "isTrue", "isFalse"
                    ),
                    "default", "equals"
                ),
                "value", Map.of(
                    "type", "string",
                    "title", "Value",
                    "description", "Value to compare against"
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
                Map.of("name", "true", "type", "any"),
                Map.of("name", "false", "type", "any")
            )
        );
    }
}
