package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler for compare datasets nodes.
 * Compares two datasets and outputs the differences including
 * matched, added, removed, and changed items.
 */
@Component
@Slf4j
public class CompareDatasetNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "compareDatasets";
    }

    @Override
    public String getDisplayName() {
        return "Compare Datasets";
    }

    @Override
    public String getDescription() {
        return "Compares two datasets and outputs the differences.";
    }

    @Override
    public String getCategory() {
        return "Data Transformation";
    }

    @Override
    public String getIcon() {
        return "diff";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        Map<String, Object> inputData = context.getInputData();

        // Get the two datasets to compare
        List<Map<String, Object>> datasetA = extractDataset(inputData, context, "inputA");
        List<Map<String, Object>> datasetB = extractDataset(inputData, context, "inputB");

        if (datasetA == null) {
            return NodeExecutionResult.failure("Dataset A is missing or invalid. Expected an array of objects.");
        }
        if (datasetB == null) {
            return NodeExecutionResult.failure("Dataset B is missing or invalid. Expected an array of objects.");
        }

        String compareKey = getStringConfig(context, "compareKey", "id");
        String mode = getStringConfig(context, "mode", "all");

        log.debug("Comparing datasets: A={} items, B={} items, key={}, mode={}",
            datasetA.size(), datasetB.size(), compareKey, mode);

        // Index datasets by compare key
        Map<String, Map<String, Object>> indexA = indexByKey(datasetA, compareKey);
        Map<String, Map<String, Object>> indexB = indexByKey(datasetB, compareKey);

        // Calculate differences
        List<Map<String, Object>> matched = new ArrayList<>();
        List<Map<String, Object>> added = new ArrayList<>();
        List<Map<String, Object>> removed = new ArrayList<>();
        List<Map<String, Object>> changed = new ArrayList<>();

        // Find removed and matched/changed items (in A but check against B)
        for (Map.Entry<String, Map<String, Object>> entry : indexA.entrySet()) {
            String key = entry.getKey();
            Map<String, Object> itemA = entry.getValue();

            if (indexB.containsKey(key)) {
                Map<String, Object> itemB = indexB.get(key);
                // Item exists in both datasets
                List<Map<String, Object>> differences = findFieldDifferences(itemA, itemB);

                if (differences.isEmpty()) {
                    // Items are identical
                    matched.add(createComparisonResult(key, itemA, itemB, List.of()));
                } else {
                    // Items have changes
                    changed.add(createComparisonResult(key, itemA, itemB, differences));
                }
            } else {
                // Item only in A (removed from B's perspective)
                removed.add(createRemovalResult(key, itemA));
            }
        }

        // Find added items (in B but not in A)
        for (Map.Entry<String, Map<String, Object>> entry : indexB.entrySet()) {
            String key = entry.getKey();
            if (!indexA.containsKey(key)) {
                added.add(createAdditionResult(key, entry.getValue()));
            }
        }

        // Build output based on mode
        Map<String, Object> output = new LinkedHashMap<>();

        switch (mode) {
            case "added":
                output.put("items", added);
                output.put("count", added.size());
                break;
            case "removed":
                output.put("items", removed);
                output.put("count", removed.size());
                break;
            case "changed":
                output.put("items", changed);
                output.put("count", changed.size());
                break;
            case "all":
            default:
                output.put("matched", matched);
                output.put("added", added);
                output.put("removed", removed);
                output.put("changed", changed);
                output.put("summary", Map.of(
                    "totalA", datasetA.size(),
                    "totalB", datasetB.size(),
                    "matchedCount", matched.size(),
                    "addedCount", added.size(),
                    "removedCount", removed.size(),
                    "changedCount", changed.size()
                ));
                break;
        }

        return NodeExecutionResult.success(output);
    }

    /**
     * Extract a dataset from input data or config.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractDataset(
            Map<String, Object> inputData,
            NodeExecutionContext context,
            String configKey) {

        // First try from input data
        if (inputData != null && inputData.containsKey(configKey)) {
            Object data = inputData.get(configKey);
            if (data instanceof List) {
                return castToMapList((List<?>) data);
            }
        }

        // Then try from config
        Object configValue = context.getNodeConfig().get(configKey);
        if (configValue instanceof List) {
            return castToMapList((List<?>) configValue);
        }

        // Then try from previous node outputs referenced in config
        String sourceNodeId = getStringConfig(context, configKey + "Source", "");
        if (!sourceNodeId.isEmpty()) {
            Object previousOutput = context.getPreviousOutput(sourceNodeId);
            if (previousOutput instanceof Map) {
                Object data = ((Map<String, Object>) previousOutput).get("data");
                if (data instanceof List) {
                    return castToMapList((List<?>) data);
                }
            }
            if (previousOutput instanceof List) {
                return castToMapList((List<?>) previousOutput);
            }
        }

        // Fallback: try default input ports
        if (inputData != null) {
            // Try "input1" / "input2" naming
            String altKey = configKey.equals("inputA") ? "input1" : "input2";
            if (inputData.containsKey(altKey)) {
                Object data = inputData.get(altKey);
                if (data instanceof List) {
                    return castToMapList((List<?>) data);
                }
            }
            // Try "data" key from input
            if ("inputA".equals(configKey) && inputData.containsKey("data")) {
                Object data = inputData.get("data");
                if (data instanceof List) {
                    return castToMapList((List<?>) data);
                }
            }
        }

        return null;
    }

    /**
     * Safely cast a List<?> to List<Map<String, Object>>.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castToMapList(List<?> list) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map) {
                result.add((Map<String, Object>) item);
            }
        }
        return result;
    }

    /**
     * Index a list of maps by a key field.
     */
    private Map<String, Map<String, Object>> indexByKey(List<Map<String, Object>> dataset, String key) {
        Map<String, Map<String, Object>> index = new LinkedHashMap<>();
        for (Map<String, Object> item : dataset) {
            Object keyValue = item.get(key);
            if (keyValue != null) {
                index.put(keyValue.toString(), item);
            }
        }
        return index;
    }

    /**
     * Find field-level differences between two maps.
     */
    private List<Map<String, Object>> findFieldDifferences(
            Map<String, Object> itemA, Map<String, Object> itemB) {
        List<Map<String, Object>> differences = new ArrayList<>();

        // Collect all keys from both items
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(itemA.keySet());
        allKeys.addAll(itemB.keySet());

        for (String field : allKeys) {
            Object valueA = itemA.get(field);
            Object valueB = itemB.get(field);

            boolean different;
            if (valueA == null && valueB == null) {
                different = false;
            } else if (valueA == null || valueB == null) {
                different = true;
            } else {
                different = !valueA.equals(valueB);
            }

            if (different) {
                Map<String, Object> diff = new LinkedHashMap<>();
                diff.put("field", field);
                diff.put("oldValue", valueA);
                diff.put("newValue", valueB);
                differences.add(diff);
            }
        }

        return differences;
    }

    /**
     * Create a comparison result for matched or changed items.
     */
    private Map<String, Object> createComparisonResult(
            String key, Map<String, Object> itemA,
            Map<String, Object> itemB, List<Map<String, Object>> differences) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("itemA", itemA);
        result.put("itemB", itemB);
        if (!differences.isEmpty()) {
            result.put("differences", differences);
        }
        return result;
    }

    /**
     * Create a result for a removed item.
     */
    private Map<String, Object> createRemovalResult(String key, Map<String, Object> item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("item", item);
        return result;
    }

    /**
     * Create a result for an added item.
     */
    private Map<String, Object> createAdditionResult(String key, Map<String, Object> item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("key", key);
        result.put("item", item);
        return result;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("inputA", Map.of(
            "type", "array",
            "title", "Dataset A",
            "description", "First dataset to compare (array of objects). Can also come from input port 'inputA'.",
            "items", Map.of("type", "object")
        ));

        properties.put("inputB", Map.of(
            "type", "array",
            "title", "Dataset B",
            "description", "Second dataset to compare (array of objects). Can also come from input port 'inputB'.",
            "items", Map.of("type", "object")
        ));

        properties.put("inputASource", Map.of(
            "type", "string",
            "title", "Dataset A Source Node",
            "description", "Node ID whose output provides Dataset A (optional)"
        ));

        properties.put("inputBSource", Map.of(
            "type", "string",
            "title", "Dataset B Source Node",
            "description", "Node ID whose output provides Dataset B (optional)"
        ));

        properties.put("compareKey", Map.of(
            "type", "string",
            "title", "Compare Key",
            "description", "Field name to use as the unique identifier for matching items",
            "default", "id"
        ));

        properties.put("mode", Map.of(
            "type", "string",
            "title", "Output Mode",
            "description", "Which differences to include in the output",
            "enum", List.of("all", "added", "removed", "changed"),
            "enumNames", List.of(
                "All (matched, added, removed, changed)",
                "Added Only (items in B but not in A)",
                "Removed Only (items in A but not in B)",
                "Changed Only (items with different values)"
            ),
            "default", "all"
        ));

        return Map.of(
            "type", "object",
            "properties", properties
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "inputA", "type", "array", "required", true,
                    "description", "First dataset (array of objects)"),
                Map.of("name", "inputB", "type", "array", "required", true,
                    "description", "Second dataset (array of objects)")
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "object",
                    "description", "Comparison results with matched, added, removed, and changed items")
            )
        );
    }
}
