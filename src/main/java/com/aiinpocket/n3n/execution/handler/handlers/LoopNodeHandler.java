package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for loop/iteration nodes.
 * Iterates over arrays and executes downstream nodes for each item.
 */
@Component
@Slf4j
public class LoopNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "loop";
    }

    @Override
    public String getDisplayName() {
        return "Loop";
    }

    @Override
    public String getDescription() {
        return "Iterates over an array and processes each item.";
    }

    @Override
    public String getCategory() {
        return "Flow Control";
    }

    @Override
    public String getIcon() {
        return "repeat";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        Map<String, Object> inputData = context.getInputData();
        String arrayField = getStringConfig(context, "arrayField", "items");
        int batchSize = getIntConfig(context, "batchSize", 1);
        boolean stopOnError = getBooleanConfig(context, "stopOnError", false);

        // Get the array to iterate
        Object arrayValue = getNestedValue(inputData, arrayField);

        if (arrayValue == null) {
            log.warn("Array field '{}' not found in input data", arrayField);
            return NodeExecutionResult.success(createOutput(List.of()));
        }

        List<?> items;
        if (arrayValue instanceof List) {
            items = (List<?>) arrayValue;
        } else if (arrayValue.getClass().isArray()) {
            items = arrayToList(arrayValue);
        } else {
            log.warn("Field '{}' is not an array, wrapping as single item", arrayField);
            items = List.of(arrayValue);
        }

        log.debug("Loop processing {} items with batch size {}", items.size(), batchSize);

        // Create batched output
        List<Map<String, Object>> batches = new ArrayList<>();

        for (int i = 0; i < items.size(); i += batchSize) {
            int end = Math.min(i + batchSize, items.size());
            List<?> batch = items.subList(i, end);

            Map<String, Object> batchOutput = new HashMap<>();
            batchOutput.put("items", batch);
            batchOutput.put("batchIndex", i / batchSize);
            batchOutput.put("totalBatches", (int) Math.ceil((double) items.size() / batchSize));
            batchOutput.put("itemsInBatch", batch.size());
            batchOutput.put("totalItems", items.size());
            batches.add(batchOutput);
        }

        Map<String, Object> output = new HashMap<>();
        output.put("batches", batches);
        output.put("totalItems", items.size());
        output.put("batchSize", batchSize);
        output.put("totalBatches", batches.size());
        output.put("stopOnError", stopOnError);

        return NodeExecutionResult.success(output);
    }

    private Object getNestedValue(Map<String, Object> data, String path) {
        if (data == null || path == null || path.isEmpty()) {
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

    private List<Object> arrayToList(Object array) {
        List<Object> list = new ArrayList<>();
        int length = java.lang.reflect.Array.getLength(array);
        for (int i = 0; i < length; i++) {
            list.add(java.lang.reflect.Array.get(array, i));
        }
        return list;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "arrayField", Map.of(
                    "type", "string",
                    "title", "Array Field",
                    "description", "Path to the array field to iterate over",
                    "default", "items"
                ),
                "batchSize", Map.of(
                    "type", "integer",
                    "title", "Batch Size",
                    "description", "Number of items to process per batch",
                    "default", 1,
                    "minimum", 1
                ),
                "stopOnError", Map.of(
                    "type", "boolean",
                    "title", "Stop on Error",
                    "description", "Stop processing if an error occurs",
                    "default", false
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
                Map.of("name", "loop", "type", "array"),
                Map.of("name", "done", "type", "any")
            )
        );
    }
}
