package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler for split out nodes.
 * Splits an array into individual items for processing.
 */
@Component
@Slf4j
public class SplitOutNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "splitOut";
    }

    @Override
    public String getDisplayName() {
        return "Split Out";
    }

    @Override
    public String getDescription() {
        return "Splits an array into individual items for separate processing.";
    }

    @Override
    public String getCategory() {
        return "Flow Control";
    }

    @Override
    public String getIcon() {
        return "split";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        Map<String, Object> inputData = context.getInputData();
        String fieldPath = getStringConfig(context, "fieldPath", "");
        boolean includeIndex = getBooleanConfig(context, "includeIndex", true);
        int batchSize = getIntConfig(context, "batchSize", 1);

        log.debug("Splitting field: {}, batchSize: {}", fieldPath, batchSize);

        Object arrayData;
        if (fieldPath.isEmpty()) {
            // Try to find the first array in input
            arrayData = inputData.values().stream()
                .filter(v -> v instanceof List)
                .findFirst()
                .orElse(inputData);
        } else {
            arrayData = getNestedValue(inputData, fieldPath);
        }

        List<Object> items;
        if (arrayData instanceof List) {
            items = (List<Object>) arrayData;
        } else if (arrayData instanceof Map) {
            // Split map entries
            items = ((Map<String, Object>) arrayData).entrySet().stream()
                .map(e -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("key", e.getKey());
                    entry.put("value", e.getValue());
                    return (Object) entry;
                })
                .collect(Collectors.toList());
        } else if (arrayData instanceof String) {
            // Split string by newlines or delimiter
            String delimiter = getStringConfig(context, "delimiter", "\n");
            items = Arrays.stream(((String) arrayData).split(delimiter))
                .map(s -> (Object) s.trim())
                .filter(s -> !((String) s).isEmpty())
                .collect(Collectors.toList());
        } else {
            items = new ArrayList<>();
            if (arrayData != null) {
                items.add(arrayData);
            }
        }

        log.debug("Split into {} items", items.size());

        // Create output with items (optionally batched)
        List<Map<String, Object>> outputItems = new ArrayList<>();

        if (batchSize <= 1) {
            // Individual items
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> item = new HashMap<>();
                item.put("item", items.get(i));
                if (includeIndex) {
                    item.put("index", i);
                    item.put("total", items.size());
                    item.put("isFirst", i == 0);
                    item.put("isLast", i == items.size() - 1);
                }
                outputItems.add(item);
            }
        } else {
            // Batched items
            for (int i = 0; i < items.size(); i += batchSize) {
                List<Object> batch = items.subList(i, Math.min(i + batchSize, items.size()));
                Map<String, Object> item = new HashMap<>();
                item.put("items", batch);
                item.put("batchIndex", i / batchSize);
                item.put("batchSize", batch.size());
                outputItems.add(item);
            }
        }

        Map<String, Object> output = new HashMap<>();
        output.put("items", outputItems);
        output.put("totalCount", items.size());
        output.put("outputCount", outputItems.size());

        return NodeExecutionResult.builder()
            .success(true)
            .output(output)
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

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "fieldPath", Map.of(
                    "type", "string",
                    "title", "Field Path",
                    "description", "Path to the array field (leave empty to auto-detect)"
                ),
                "includeIndex", Map.of(
                    "type", "boolean",
                    "title", "Include Index",
                    "description", "Include index information in output",
                    "default", true
                ),
                "batchSize", Map.of(
                    "type", "integer",
                    "title", "Batch Size",
                    "description", "Number of items per batch (1 = no batching)",
                    "minimum", 1,
                    "default", 1
                ),
                "delimiter", Map.of(
                    "type", "string",
                    "title", "Delimiter",
                    "description", "Delimiter for splitting strings",
                    "default", "\\n"
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
                Map.of("name", "items", "type", "array")
            )
        );
    }
}
