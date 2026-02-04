package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler for aggregate nodes.
 * Aggregates multiple items into one using various methods.
 */
@Component
@Slf4j
public class AggregateNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "aggregate";
    }

    @Override
    public String getDisplayName() {
        return "Aggregate";
    }

    @Override
    public String getDescription() {
        return "Aggregates multiple items into one using various methods (sum, count, average, etc.).";
    }

    @Override
    public String getCategory() {
        return "Data Transform";
    }

    @Override
    public String getIcon() {
        return "group";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        Map<String, Object> inputData = context.getInputData();
        String operation = getStringConfig(context, "operation", "collect");
        String field = getStringConfig(context, "field", "");
        String groupByField = getStringConfig(context, "groupBy", "");
        String inputKey = getStringConfig(context, "inputKey", "items");

        // Get array to aggregate
        Object input = inputData.get(inputKey);
        if (input == null) {
            input = inputData.values().stream()
                .filter(v -> v instanceof List)
                .findFirst()
                .orElse(List.of(inputData));
        }

        List<Object> items;
        if (input instanceof List) {
            items = (List<Object>) input;
        } else {
            items = List.of(input);
        }

        log.debug("Aggregating {} items with operation: {}", items.size(), operation);

        Map<String, Object> output = new HashMap<>();

        if (!groupByField.isEmpty()) {
            // Group by field first
            Map<Object, List<Object>> groups = items.stream()
                .collect(Collectors.groupingBy(item -> getFieldValue(item, groupByField)));

            Map<String, Object> groupResults = new LinkedHashMap<>();
            for (Map.Entry<Object, List<Object>> group : groups.entrySet()) {
                groupResults.put(
                    group.getKey() != null ? group.getKey().toString() : "null",
                    aggregateItems(group.getValue(), operation, field)
                );
            }
            output.put("groups", groupResults);
            output.put("groupCount", groups.size());
        } else {
            // Single aggregation
            Object result = aggregateItems(items, operation, field);
            output.put("result", result);
        }

        output.put("inputCount", items.size());
        output.put("operation", operation);

        return NodeExecutionResult.builder()
            .success(true)
            .output(output)
            .build();
    }

    @SuppressWarnings("unchecked")
    private Object aggregateItems(List<Object> items, String operation, String field) {
        switch (operation) {
            case "collect":
                // Collect all items into array
                return items;

            case "count":
                return items.size();

            case "sum":
                return items.stream()
                    .map(item -> getNumericValue(item, field))
                    .filter(Objects::nonNull)
                    .mapToDouble(Number::doubleValue)
                    .sum();

            case "average":
            case "avg":
                OptionalDouble avg = items.stream()
                    .map(item -> getNumericValue(item, field))
                    .filter(Objects::nonNull)
                    .mapToDouble(Number::doubleValue)
                    .average();
                return avg.isPresent() ? avg.getAsDouble() : null;

            case "min":
                return items.stream()
                    .map(item -> getNumericValue(item, field))
                    .filter(Objects::nonNull)
                    .mapToDouble(Number::doubleValue)
                    .min()
                    .orElse(Double.NaN);

            case "max":
                return items.stream()
                    .map(item -> getNumericValue(item, field))
                    .filter(Objects::nonNull)
                    .mapToDouble(Number::doubleValue)
                    .max()
                    .orElse(Double.NaN);

            case "first":
                return items.isEmpty() ? null : items.get(0);

            case "last":
                return items.isEmpty() ? null : items.get(items.size() - 1);

            case "merge":
                // Merge all objects into one
                Map<String, Object> merged = new LinkedHashMap<>();
                for (Object item : items) {
                    if (item instanceof Map) {
                        merged.putAll((Map<String, Object>) item);
                    }
                }
                return merged;

            case "concat":
                // Concatenate string values
                return items.stream()
                    .map(item -> {
                        Object val = field.isEmpty() ? item : getFieldValue(item, field);
                        return val != null ? val.toString() : "";
                    })
                    .collect(Collectors.joining());

            case "unique":
                // Get unique values
                return items.stream()
                    .map(item -> field.isEmpty() ? item : getFieldValue(item, field))
                    .distinct()
                    .collect(Collectors.toList());

            default:
                log.warn("Unknown aggregation operation: {}", operation);
                return items;
        }
    }

    @SuppressWarnings("unchecked")
    private Object getFieldValue(Object item, String field) {
        if (field == null || field.isEmpty()) {
            return item;
        }

        if (item instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) item;
            String[] parts = field.split("\\.");
            Object current = map;

            for (String part : parts) {
                if (current instanceof Map) {
                    current = ((Map<String, Object>) current).get(part);
                } else {
                    return null;
                }
            }

            return current;
        }

        return item;
    }

    private Number getNumericValue(Object item, String field) {
        Object value = field.isEmpty() ? item : getFieldValue(item, field);

        if (value instanceof Number) {
            return (Number) value;
        }

        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "inputKey", Map.of(
                    "type", "string",
                    "title", "Input Key",
                    "description", "Key containing the array to aggregate",
                    "default", "items"
                ),
                "operation", Map.of(
                    "type", "string",
                    "title", "Operation",
                    "enum", List.of(
                        "collect", "count", "sum", "average", "min", "max",
                        "first", "last", "merge", "concat", "unique"
                    ),
                    "enumNames", List.of(
                        "Collect (keep all)",
                        "Count",
                        "Sum",
                        "Average",
                        "Minimum",
                        "Maximum",
                        "First Item",
                        "Last Item",
                        "Merge Objects",
                        "Concatenate Strings",
                        "Unique Values"
                    ),
                    "default", "collect"
                ),
                "field", Map.of(
                    "type", "string",
                    "title", "Field",
                    "description", "Field path for numeric operations"
                ),
                "groupBy", Map.of(
                    "type", "string",
                    "title", "Group By",
                    "description", "Field path to group items by before aggregating"
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
                Map.of("name", "output", "type", "any")
            )
        );
    }
}
