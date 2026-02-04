package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler for sort nodes.
 * Sorts data items by specified criteria.
 */
@Component
@Slf4j
public class SortNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "sort";
    }

    @Override
    public String getDisplayName() {
        return "Sort";
    }

    @Override
    public String getDescription() {
        return "Sorts data items by specified field and order.";
    }

    @Override
    public String getCategory() {
        return "Data Transform";
    }

    @Override
    public String getIcon() {
        return "sort-ascending";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        Map<String, Object> inputData = context.getInputData();
        String sortField = getStringConfig(context, "sortField", "");
        String order = getStringConfig(context, "order", "ascending");
        String sortType = getStringConfig(context, "sortType", "auto");
        String inputKey = getStringConfig(context, "inputKey", "items");

        log.debug("Sorting by field: {}, order: {}", sortField, order);

        // Get array to sort
        Object input = inputData.get(inputKey);
        if (input == null) {
            input = inputData.values().stream()
                .filter(v -> v instanceof List)
                .findFirst()
                .orElse(null);
        }

        if (!(input instanceof List)) {
            return NodeExecutionResult.builder()
                .success(true)
                .output(inputData)
                .build();
        }

        List<Object> items = new ArrayList<>((List<Object>) input);
        boolean ascending = "ascending".equals(order) || "asc".equals(order);

        // Sort the items
        items.sort((a, b) -> {
            Object valA = getFieldValue(a, sortField);
            Object valB = getFieldValue(b, sortField);

            int result = compareValues(valA, valB, sortType);
            return ascending ? result : -result;
        });

        Map<String, Object> output = new HashMap<>(inputData);
        output.put(inputKey, items);
        output.put("_sorted", true);
        output.put("_sortedBy", sortField);
        output.put("_sortOrder", order);

        return NodeExecutionResult.builder()
            .success(true)
            .output(output)
            .build();
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

    private int compareValues(Object a, Object b, String sortType) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;

        switch (sortType) {
            case "number":
                return compareAsNumbers(a, b);
            case "string":
                return a.toString().compareTo(b.toString());
            case "date":
                return compareAsDates(a, b);
            case "auto":
            default:
                // Try to determine type automatically
                if (isNumeric(a) && isNumeric(b)) {
                    return compareAsNumbers(a, b);
                }
                return a.toString().compareToIgnoreCase(b.toString());
        }
    }

    private boolean isNumeric(Object value) {
        if (value instanceof Number) return true;
        try {
            Double.parseDouble(value.toString());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private int compareAsNumbers(Object a, Object b) {
        try {
            double numA = Double.parseDouble(a.toString());
            double numB = Double.parseDouble(b.toString());
            return Double.compare(numA, numB);
        } catch (NumberFormatException e) {
            return a.toString().compareTo(b.toString());
        }
    }

    private int compareAsDates(Object a, Object b) {
        try {
            // Try ISO date format
            java.time.Instant dateA = java.time.Instant.parse(a.toString());
            java.time.Instant dateB = java.time.Instant.parse(b.toString());
            return dateA.compareTo(dateB);
        } catch (Exception e) {
            // Fall back to string comparison
            return a.toString().compareTo(b.toString());
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
                    "description", "Key containing the array to sort",
                    "default", "items"
                ),
                "sortField", Map.of(
                    "type", "string",
                    "title", "Sort Field",
                    "description", "Field path to sort by (e.g., data.name)"
                ),
                "order", Map.of(
                    "type", "string",
                    "title", "Order",
                    "enum", List.of("ascending", "descending"),
                    "default", "ascending"
                ),
                "sortType", Map.of(
                    "type", "string",
                    "title", "Sort Type",
                    "enum", List.of("auto", "string", "number", "date"),
                    "default", "auto"
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
                Map.of("name", "output", "type", "array")
            )
        );
    }
}
