package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler for remove duplicates nodes.
 * Removes duplicate items from data arrays.
 */
@Component
@Slf4j
public class RemoveDuplicatesNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "removeDuplicates";
    }

    @Override
    public String getDisplayName() {
        return "Remove Duplicates";
    }

    @Override
    public String getDescription() {
        return "Removes duplicate items from data arrays based on specified criteria.";
    }

    @Override
    public String getCategory() {
        return "Data Transform";
    }

    @Override
    public String getIcon() {
        return "delete";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        Map<String, Object> inputData = context.getInputData();
        String compareField = getStringConfig(context, "compareField", "");
        String keepMode = getStringConfig(context, "keepMode", "first");
        String inputKey = getStringConfig(context, "inputKey", "items");
        boolean caseSensitive = getBooleanConfig(context, "caseSensitive", true);

        // Get array to deduplicate
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

        List<Object> items = (List<Object>) input;
        int originalCount = items.size();

        log.debug("Removing duplicates from {} items, compareField: {}", originalCount, compareField);

        List<Object> unique;
        List<Object> duplicates = new ArrayList<>();

        if (compareField.isEmpty()) {
            // Compare entire objects
            Set<String> seen = new LinkedHashSet<>();
            unique = new ArrayList<>();

            for (Object item : items) {
                String key = getComparisonKey(item, caseSensitive);
                if (!seen.contains(key)) {
                    seen.add(key);
                    unique.add(item);
                } else {
                    duplicates.add(item);
                }
            }
        } else {
            // Compare by specific field
            Map<String, Object> seenByKey = new LinkedHashMap<>();
            unique = new ArrayList<>();

            for (Object item : items) {
                Object fieldValue = getFieldValue(item, compareField);
                String key = fieldValue != null ?
                    (caseSensitive ? fieldValue.toString() : fieldValue.toString().toLowerCase()) :
                    "null";

                if (!seenByKey.containsKey(key)) {
                    seenByKey.put(key, item);
                    unique.add(item);
                } else {
                    if ("last".equals(keepMode)) {
                        // Replace with newer item
                        Object oldItem = seenByKey.get(key);
                        duplicates.add(oldItem);
                        unique.remove(oldItem);
                        seenByKey.put(key, item);
                        unique.add(item);
                    } else {
                        // Keep first (default)
                        duplicates.add(item);
                    }
                }
            }
        }

        int removedCount = originalCount - unique.size();
        log.debug("Removed {} duplicates, {} unique items remain", removedCount, unique.size());

        Map<String, Object> output = new HashMap<>(inputData);
        output.put(inputKey, unique);
        output.put("duplicates", duplicates);
        output.put("uniqueCount", unique.size());
        output.put("duplicateCount", duplicates.size());
        output.put("removedCount", removedCount);

        return NodeExecutionResult.builder()
            .success(true)
            .output(output)
            .build();
    }

    private String getComparisonKey(Object item, boolean caseSensitive) {
        String key;
        if (item instanceof Map) {
            // Sort keys for consistent comparison
            TreeMap<String, Object> sorted = new TreeMap<>((Map<String, Object>) item);
            key = sorted.toString();
        } else {
            key = item != null ? item.toString() : "null";
        }
        return caseSensitive ? key : key.toLowerCase();
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

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "inputKey", Map.of(
                    "type", "string",
                    "title", "Input Key",
                    "description", "Key containing the array to deduplicate",
                    "default", "items"
                ),
                "compareField", Map.of(
                    "type", "string",
                    "title", "Compare Field",
                    "description", "Field path to use for comparison (empty = compare entire items)"
                ),
                "keepMode", Map.of(
                    "type", "string",
                    "title", "Keep",
                    "enum", List.of("first", "last"),
                    "enumNames", List.of("First occurrence", "Last occurrence"),
                    "default", "first"
                ),
                "caseSensitive", Map.of(
                    "type", "boolean",
                    "title", "Case Sensitive",
                    "description", "Whether string comparison is case sensitive",
                    "default", true
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
                Map.of("name", "unique", "type", "array"),
                Map.of("name", "duplicates", "type", "array")
            )
        );
    }
}
