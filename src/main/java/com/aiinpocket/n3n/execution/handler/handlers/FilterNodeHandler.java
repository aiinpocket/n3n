package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler for filter nodes.
 * Filters data items based on conditions.
 */
@Component
@Slf4j
public class FilterNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "filter";
    }

    @Override
    public String getDisplayName() {
        return "Filter";
    }

    @Override
    public String getDescription() {
        return "Filters data items based on specified conditions.";
    }

    @Override
    public String getCategory() {
        return "Flow Control";
    }

    @Override
    public String getIcon() {
        return "filter";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        Map<String, Object> inputData = context.getInputData();
        String field = getStringConfig(context, "field", "");
        String operator = getStringConfig(context, "operator", "exists");
        Object compareValue = context.getNodeConfig().get("value");
        String inputKey = getStringConfig(context, "inputKey", "items");

        log.debug("Filtering with condition: {} {} {}", field, operator, compareValue);

        Object input = inputData.get(inputKey);
        if (input == null) {
            // Try to use the entire input as array
            input = inputData.values().stream()
                .filter(v -> v instanceof List)
                .findFirst()
                .orElse(inputData);
        }

        List<Object> items;
        if (input instanceof List) {
            items = (List<Object>) input;
        } else if (input instanceof Map) {
            items = List.of(input);
        } else {
            items = new ArrayList<>();
        }

        // Filter items
        List<Object> filtered = items.stream()
            .filter(item -> matchesCondition(item, field, operator, compareValue))
            .collect(Collectors.toList());

        List<Object> rejected = items.stream()
            .filter(item -> !matchesCondition(item, field, operator, compareValue))
            .collect(Collectors.toList());

        log.debug("Filtered {} items to {} (rejected {})", items.size(), filtered.size(), rejected.size());

        Map<String, Object> output = new HashMap<>();
        output.put("filtered", filtered);
        output.put("rejected", rejected);
        output.put("count", filtered.size());
        output.put("rejectedCount", rejected.size());

        return NodeExecutionResult.builder()
            .success(true)
            .output(output)
            .build();
    }

    @SuppressWarnings("unchecked")
    private boolean matchesCondition(Object item, String field, String operator, Object compareValue) {
        Object fieldValue;

        if (field == null || field.isEmpty()) {
            fieldValue = item;
        } else if (item instanceof Map) {
            fieldValue = getNestedValue((Map<String, Object>) item, field);
        } else {
            fieldValue = item;
        }

        return evaluateCondition(fieldValue, operator, compareValue);
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
        switch (operator) {
            case "exists":
                return fieldValue != null;

            case "notExists":
                return fieldValue == null;

            case "equals":
                return Objects.equals(
                    fieldValue != null ? fieldValue.toString() : null,
                    compareValue != null ? compareValue.toString() : null
                );

            case "notEquals":
                return !Objects.equals(
                    fieldValue != null ? fieldValue.toString() : null,
                    compareValue != null ? compareValue.toString() : null
                );

            case "contains":
                return fieldValue != null && compareValue != null &&
                    fieldValue.toString().contains(compareValue.toString());

            case "notContains":
                return fieldValue == null || compareValue == null ||
                    !fieldValue.toString().contains(compareValue.toString());

            case "greaterThan":
                return compareNumbers(fieldValue, compareValue) > 0;

            case "lessThan":
                return compareNumbers(fieldValue, compareValue) < 0;

            case "greaterOrEqual":
                return compareNumbers(fieldValue, compareValue) >= 0;

            case "lessOrEqual":
                return compareNumbers(fieldValue, compareValue) <= 0;

            case "isEmpty":
                return fieldValue == null || fieldValue.toString().isEmpty();

            case "isNotEmpty":
                return fieldValue != null && !fieldValue.toString().isEmpty();

            case "isTrue":
                return fieldValue != null && Boolean.parseBoolean(fieldValue.toString());

            case "isFalse":
                return fieldValue == null || !Boolean.parseBoolean(fieldValue.toString());

            case "regex":
                return fieldValue != null && compareValue != null &&
                    fieldValue.toString().matches(compareValue.toString());

            default:
                log.warn("Unknown filter operator: {}", operator);
                return true;
        }
    }

    private int compareNumbers(Object a, Object b) {
        try {
            double numA = a != null ? Double.parseDouble(a.toString()) : 0;
            double numB = b != null ? Double.parseDouble(b.toString()) : 0;
            return Double.compare(numA, numB);
        } catch (NumberFormatException e) {
            String strA = a != null ? a.toString() : "";
            String strB = b != null ? b.toString() : "";
            return strA.compareTo(strB);
        }
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "inputKey", Map.of(
                    "type", "string",
                    "title", "Input Key",
                    "description", "Key containing the array to filter",
                    "default", "items"
                ),
                "field", Map.of(
                    "type", "string",
                    "title", "Field",
                    "description", "Field path to check (e.g., data.status)"
                ),
                "operator", Map.of(
                    "type", "string",
                    "title", "Operator",
                    "enum", List.of(
                        "exists", "notExists", "equals", "notEquals",
                        "contains", "notContains", "greaterThan", "lessThan",
                        "greaterOrEqual", "lessOrEqual", "isEmpty", "isNotEmpty",
                        "isTrue", "isFalse", "regex"
                    ),
                    "default", "exists"
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
                Map.of("name", "input", "type", "array", "required", true)
            ),
            "outputs", List.of(
                Map.of("name", "filtered", "type", "array"),
                Map.of("name", "rejected", "type", "array")
            )
        );
    }
}
