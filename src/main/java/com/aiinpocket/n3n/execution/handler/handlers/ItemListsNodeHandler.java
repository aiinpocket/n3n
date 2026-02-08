package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler for item lists operations.
 * Provides advanced list manipulation: concatenate, split, limit, flatten,
 * shuffle, unique, sort by field, group by field, chunk, zip, etc.
 */
@Component
@Slf4j
public class ItemListsNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "itemLists";
    }

    @Override
    public String getDisplayName() {
        return "Item Lists";
    }

    @Override
    public String getDescription() {
        return "Advanced list operations: concatenate, split, limit, flatten, shuffle, chunk, etc.";
    }

    @Override
    public String getCategory() {
        return NodeCategory.DATA_TRANSFORM;
    }

    @Override
    public String getIcon() {
        return "unorderedList";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String operation = getStringConfig(context, "operation", "concatenate");
        Map<String, Object> inputData = context.getInputData();

        List<Object> items = extractItems(inputData, context);

        log.debug("ItemLists operation: {}, items count: {}", operation, items != null ? items.size() : 0);

        try {
            Map<String, Object> output = switch (operation) {
                case "concatenate" -> doConcatenate(inputData, context);
                case "limit" -> doLimit(items, context);
                case "offset" -> doOffset(items, context);
                case "slice" -> doSlice(items, context);
                case "shuffle" -> doShuffle(items);
                case "unique" -> doUnique(items, context);
                case "flatten" -> doFlatten(items, context);
                case "chunk" -> doChunk(items, context);
                case "reverse" -> doReverse(items);
                case "first" -> doFirst(items, context);
                case "last" -> doLast(items, context);
                case "zip" -> doZip(inputData, context);
                case "groupBy" -> doGroupBy(items, context);
                case "count" -> doCount(items);
                case "isEmpty" -> doIsEmpty(items);
                case "contains" -> doContains(items, context);
                case "indexOf" -> doIndexOf(items, context);
                case "pluck" -> doPluck(items, context);
                case "summarize" -> doSummarize(items, context);
                default -> throw new IllegalArgumentException("Unknown operation: " + operation);
            };

            return NodeExecutionResult.success(output);
        } catch (Exception e) {
            return NodeExecutionResult.failure("ItemLists error (" + operation + "): " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Object> extractItems(Map<String, Object> inputData, NodeExecutionContext context) {
        if (inputData == null) return new ArrayList<>();

        // Try "items" key first
        Object items = inputData.get("items");
        if (items instanceof List) return new ArrayList<>((List<Object>) items);

        // Try "data" key
        Object data = inputData.get("data");
        if (data instanceof List) return new ArrayList<>((List<Object>) data);

        // Try "input" key
        Object input = inputData.get("input");
        if (input instanceof List) return new ArrayList<>((List<Object>) input);

        // Try from config
        Object configItems = context.getNodeConfig().get("items");
        if (configItems instanceof List) return new ArrayList<>((List<Object>) configItems);

        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doConcatenate(Map<String, Object> inputData, NodeExecutionContext context) {
        List<Object> result = new ArrayList<>();

        // Concatenate from multiple input ports
        for (String key : List.of("inputA", "inputB", "inputC", "input1", "input2", "input3", "items", "data")) {
            if (inputData != null && inputData.containsKey(key)) {
                Object val = inputData.get(key);
                if (val instanceof List) {
                    result.addAll((List<Object>) val);
                } else if (val != null) {
                    result.add(val);
                }
            }
        }

        return Map.of("items", result, "count", result.size());
    }

    private Map<String, Object> doLimit(List<Object> items, NodeExecutionContext context) {
        int limit = getIntConfig(context, "limit", 10);
        if (items == null || items.isEmpty()) return Map.of("items", List.of(), "count", 0);
        List<Object> result = items.stream().limit(limit).collect(Collectors.toList());
        return Map.of("items", result, "count", result.size());
    }

    private Map<String, Object> doOffset(List<Object> items, NodeExecutionContext context) {
        int offset = getIntConfig(context, "offset", 0);
        if (items == null || items.isEmpty()) return Map.of("items", List.of(), "count", 0);
        List<Object> result = items.stream().skip(offset).collect(Collectors.toList());
        return Map.of("items", result, "count", result.size());
    }

    private Map<String, Object> doSlice(List<Object> items, NodeExecutionContext context) {
        int start = getIntConfig(context, "start", 0);
        int end = getIntConfig(context, "end", -1);
        if (items == null || items.isEmpty()) return Map.of("items", List.of(), "count", 0);

        if (start < 0) start = Math.max(0, items.size() + start);
        if (end < 0) end = items.size();
        end = Math.min(end, items.size());
        start = Math.min(start, end);

        List<Object> result = items.subList(start, end);
        return Map.of("items", new ArrayList<>(result), "count", result.size());
    }

    private Map<String, Object> doShuffle(List<Object> items) {
        if (items == null || items.isEmpty()) return Map.of("items", List.of(), "count", 0);
        List<Object> result = new ArrayList<>(items);
        Collections.shuffle(result);
        return Map.of("items", result, "count", result.size());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doUnique(List<Object> items, NodeExecutionContext context) {
        if (items == null || items.isEmpty()) return Map.of("items", List.of(), "count", 0);
        String field = getStringConfig(context, "field", "");

        if (field.isEmpty()) {
            List<Object> result = new ArrayList<>(new LinkedHashSet<>(items));
            return Map.of("items", result, "count", result.size());
        }

        // Unique by field
        Map<Object, Object> seen = new LinkedHashMap<>();
        for (Object item : items) {
            if (item instanceof Map) {
                Object key = ((Map<String, Object>) item).get(field);
                if (!seen.containsKey(key)) {
                    seen.put(key, item);
                }
            }
        }
        List<Object> result = new ArrayList<>(seen.values());
        return Map.of("items", result, "count", result.size());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doFlatten(List<Object> items, NodeExecutionContext context) {
        if (items == null || items.isEmpty()) return Map.of("items", List.of(), "count", 0);
        int depth = getIntConfig(context, "depth", 1);
        List<Object> result = flattenRecursive(items, depth);
        return Map.of("items", result, "count", result.size());
    }

    @SuppressWarnings("unchecked")
    private List<Object> flattenRecursive(List<Object> items, int depth) {
        if (depth <= 0) return items;
        List<Object> result = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof List) {
                result.addAll(flattenRecursive((List<Object>) item, depth - 1));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    private Map<String, Object> doChunk(List<Object> items, NodeExecutionContext context) {
        int chunkSize = getIntConfig(context, "chunkSize", 5);
        if (chunkSize < 1) chunkSize = 1;
        if (items == null || items.isEmpty()) return Map.of("chunks", List.of(), "count", 0);

        List<List<Object>> chunks = new ArrayList<>();
        for (int i = 0; i < items.size(); i += chunkSize) {
            chunks.add(items.subList(i, Math.min(i + chunkSize, items.size())));
        }
        return Map.of("chunks", chunks, "count", chunks.size());
    }

    private Map<String, Object> doReverse(List<Object> items) {
        if (items == null || items.isEmpty()) return Map.of("items", List.of(), "count", 0);
        List<Object> result = new ArrayList<>(items);
        Collections.reverse(result);
        return Map.of("items", result, "count", result.size());
    }

    private Map<String, Object> doFirst(List<Object> items, NodeExecutionContext context) {
        int n = getIntConfig(context, "count", 1);
        if (items == null || items.isEmpty()) return Map.of("items", List.of(), "count", 0);
        List<Object> result = items.stream().limit(n).collect(Collectors.toList());
        return Map.of("items", result, "count", result.size());
    }

    private Map<String, Object> doLast(List<Object> items, NodeExecutionContext context) {
        int n = getIntConfig(context, "count", 1);
        if (items == null || items.isEmpty()) return Map.of("items", List.of(), "count", 0);
        int skip = Math.max(0, items.size() - n);
        List<Object> result = items.stream().skip(skip).collect(Collectors.toList());
        return Map.of("items", result, "count", result.size());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doZip(Map<String, Object> inputData, NodeExecutionContext context) {
        List<Object> listA = inputData != null && inputData.get("inputA") instanceof List
            ? (List<Object>) inputData.get("inputA") : List.of();
        List<Object> listB = inputData != null && inputData.get("inputB") instanceof List
            ? (List<Object>) inputData.get("inputB") : List.of();

        int maxLen = Math.max(listA.size(), listB.size());
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < maxLen; i++) {
            Map<String, Object> pair = new LinkedHashMap<>();
            pair.put("a", i < listA.size() ? listA.get(i) : null);
            pair.put("b", i < listB.size() ? listB.get(i) : null);
            pair.put("index", i);
            result.add(pair);
        }

        return Map.of("items", result, "count", result.size());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doGroupBy(List<Object> items, NodeExecutionContext context) {
        String field = getStringConfig(context, "field", "");
        if (field.isEmpty()) {
            return Map.of("groups", Map.of(), "error", "Field name is required for groupBy");
        }
        if (items == null || items.isEmpty()) return Map.of("groups", Map.of());

        Map<String, List<Object>> groups = new LinkedHashMap<>();
        for (Object item : items) {
            String key = "null";
            if (item instanceof Map) {
                Object val = ((Map<String, Object>) item).get(field);
                key = val != null ? val.toString() : "null";
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        return Map.of("groups", groups, "groupCount", groups.size());
    }

    private Map<String, Object> doCount(List<Object> items) {
        int count = items != null ? items.size() : 0;
        return Map.of("count", count);
    }

    private Map<String, Object> doIsEmpty(List<Object> items) {
        boolean empty = items == null || items.isEmpty();
        return Map.of("isEmpty", empty, "count", items != null ? items.size() : 0);
    }

    private Map<String, Object> doContains(List<Object> items, NodeExecutionContext context) {
        String value = getStringConfig(context, "value", "");
        String field = getStringConfig(context, "field", "");

        if (items == null || items.isEmpty()) {
            return Map.of("contains", false, "index", -1);
        }

        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (field.isEmpty()) {
                if (value.equals(String.valueOf(item))) {
                    return Map.of("contains", true, "index", i);
                }
            } else if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) item;
                Object fieldVal = map.get(field);
                if (fieldVal != null && value.equals(fieldVal.toString())) {
                    return Map.of("contains", true, "index", i);
                }
            }
        }

        return Map.of("contains", false, "index", -1);
    }

    private Map<String, Object> doIndexOf(List<Object> items, NodeExecutionContext context) {
        return doContains(items, context); // Same logic
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doPluck(List<Object> items, NodeExecutionContext context) {
        String field = getStringConfig(context, "field", "");
        if (field.isEmpty() || items == null) {
            return Map.of("values", List.of());
        }

        List<Object> values = items.stream()
            .filter(item -> item instanceof Map)
            .map(item -> ((Map<String, Object>) item).get(field))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        return Map.of("values", values, "count", values.size());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doSummarize(List<Object> items, NodeExecutionContext context) {
        String field = getStringConfig(context, "field", "");
        if (field.isEmpty() || items == null || items.isEmpty()) {
            return Map.of("count", 0);
        }

        List<Double> numbers = items.stream()
            .filter(item -> item instanceof Map)
            .map(item -> ((Map<String, Object>) item).get(field))
            .filter(Objects::nonNull)
            .map(val -> {
                try { return Double.parseDouble(val.toString()); }
                catch (NumberFormatException e) { return null; }
            })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (numbers.isEmpty()) {
            return Map.of("count", items.size(), "numericValues", 0);
        }

        double sum = numbers.stream().mapToDouble(Double::doubleValue).sum();
        double avg = sum / numbers.size();
        double min = numbers.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        double max = numbers.stream().mapToDouble(Double::doubleValue).max().orElse(0);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("count", items.size());
        output.put("numericValues", numbers.size());
        output.put("sum", sum);
        output.put("average", avg);
        output.put("min", min);
        output.put("max", max);

        return output;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("operation", Map.of(
            "type", "string",
            "title", "Operation",
            "enum", List.of(
                "concatenate", "limit", "offset", "slice", "shuffle",
                "unique", "flatten", "chunk", "reverse", "first", "last",
                "zip", "groupBy", "count", "isEmpty", "contains",
                "indexOf", "pluck", "summarize"
            ),
            "default", "concatenate",
            "description", "The list operation to perform"
        ));

        properties.put("limit", Map.of("type", "integer", "title", "Limit", "default", 10));
        properties.put("offset", Map.of("type", "integer", "title", "Offset", "default", 0));
        properties.put("start", Map.of("type", "integer", "title", "Start Index", "default", 0));
        properties.put("end", Map.of("type", "integer", "title", "End Index", "default", -1));
        properties.put("chunkSize", Map.of("type", "integer", "title", "Chunk Size", "default", 5));
        properties.put("field", Map.of("type", "string", "title", "Field Name"));
        properties.put("value", Map.of("type", "string", "title", "Search Value"));
        properties.put("depth", Map.of("type", "integer", "title", "Flatten Depth", "default", 1));
        properties.put("count", Map.of("type", "integer", "title", "Count", "default", 1));

        return Map.of(
            "type", "object",
            "properties", properties,
            "required", List.of("operation")
        );
    }
}
