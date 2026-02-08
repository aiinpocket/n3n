package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * JSON operation tool
 *
 * Allows AI Agent to perform JSON data operations:
 * - Parse JSON strings
 * - Extract values by path
 * - Format/prettify JSON
 * - Merge JSON objects
 * - Validate JSON format
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JsonTool implements AgentNodeTool {

    private final ObjectMapper objectMapper;

    @Override
    public String getId() {
        return "json";
    }

    @Override
    public String getName() {
        return "JSON Tool";
    }

    @Override
    public String getDescription() {
        return """
                JSON data operation tool. Supported operations:
                - parse: Parse JSON string
                - get: Extract value by path (e.g. user.name, items[0].id)
                - format: Prettify JSON (indented formatting)
                - minify: Compact JSON (remove whitespace)
                - validate: Validate JSON format
                - merge: Merge multiple JSON objects
                - keys: Get all keys of an object
                - values: Get all values of an object
                - count: Count array length or object property count
                - filter: Filter array elements

                Path syntax:
                - Dot notation: user.profile.name
                - Array index: items[0], items[-1] (last element)
                - Wildcard: items[*].id (id of all elements)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("parse", "get", "format", "minify", "validate", "merge", "keys", "values", "count", "filter"),
                                "description", "Operation type",
                                "default", "parse"
                        ),
                        "json", Map.of(
                                "type", "string",
                                "description", "JSON string"
                        ),
                        "path", Map.of(
                                "type", "string",
                                "description", "JSON path (for get operation)"
                        ),
                        "json2", Map.of(
                                "type", "string",
                                "description", "Second JSON (for merge operation)"
                        ),
                        "condition", Map.of(
                                "type", "string",
                                "description", "Filter condition (for filter operation, e.g. status=active)"
                        )
                ),
                "required", List.of("json")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String jsonStr = (String) parameters.get("json");
                if (jsonStr == null || jsonStr.isBlank()) {
                    return ToolResult.failure("JSON string cannot be empty");
                }

                String operation = (String) parameters.getOrDefault("operation", "parse");

                return switch (operation.toLowerCase()) {
                    case "parse" -> handleParse(jsonStr);
                    case "get" -> handleGet(jsonStr, (String) parameters.get("path"));
                    case "format" -> handleFormat(jsonStr);
                    case "minify" -> handleMinify(jsonStr);
                    case "validate" -> handleValidate(jsonStr);
                    case "merge" -> handleMerge(jsonStr, (String) parameters.get("json2"));
                    case "keys" -> handleKeys(jsonStr);
                    case "values" -> handleValues(jsonStr);
                    case "count" -> handleCount(jsonStr);
                    case "filter" -> handleFilter(jsonStr, (String) parameters.get("condition"));
                    default -> ToolResult.failure("Unsupported operation: " + operation);
                };

            } catch (JsonProcessingException e) {
                log.error("JSON parsing failed", e);
                return ToolResult.failure("JSON parsing error");
            } catch (Exception e) {
                log.error("JSON operation failed", e);
                return ToolResult.failure("JSON operation failed");
            }
        });
    }

    /**
     * Parse JSON
     */
    private ToolResult handleParse(String jsonStr) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonStr);
        String type = getNodeType(node);

        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("parsed", objectMapper.convertValue(node, Object.class));

        if (node.isObject()) {
            data.put("keys", getObjectKeys(node));
            data.put("property_count", node.size());
        } else if (node.isArray()) {
            data.put("length", node.size());
        }

        return ToolResult.success(
                String.format("JSON type: %s\n%s", type, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node)),
                data
        );
    }

    /**
     * Get value by path
     */
    private ToolResult handleGet(String jsonStr, String path) throws JsonProcessingException {
        if (path == null || path.isBlank()) {
            return ToolResult.failure("The get operation requires a path parameter");
        }

        JsonNode root = objectMapper.readTree(jsonStr);
        JsonNode result = getByPath(root, path);

        if (result == null || result.isMissingNode()) {
            return ToolResult.success(
                    String.format("No value found at path '%s'", path),
                    Map.of("path", path, "found", false)
            );
        }

        Object value = objectMapper.convertValue(result, Object.class);

        return ToolResult.success(
                String.format("Value at path '%s': %s", path, result.toString()),
                Map.of(
                        "path", path,
                        "value", value,
                        "type", getNodeType(result),
                        "found", true
                )
        );
    }

    /**
     * Format JSON
     */
    private ToolResult handleFormat(String jsonStr) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonStr);
        String formatted = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);

        return ToolResult.success(
                formatted,
                Map.of("formatted", formatted)
        );
    }

    /**
     * Minify JSON
     */
    private ToolResult handleMinify(String jsonStr) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonStr);
        ObjectMapper compactMapper = objectMapper.copy();
        compactMapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        String minified = compactMapper.writeValueAsString(node);

        return ToolResult.success(
                minified,
                Map.of(
                        "minified", minified,
                        "original_length", jsonStr.length(),
                        "minified_length", minified.length(),
                        "saved", jsonStr.length() - minified.length()
                )
        );
    }

    /**
     * Validate JSON
     */
    private ToolResult handleValidate(String jsonStr) {
        try {
            JsonNode node = objectMapper.readTree(jsonStr);
            String type = getNodeType(node);

            return ToolResult.success(
                    "Valid JSON (" + type + ")",
                    Map.of("valid", true, "type", type)
            );
        } catch (JsonProcessingException e) {
            return ToolResult.success(
                    "Invalid JSON: " + e.getOriginalMessage(),
                    Map.of("valid", false, "error", e.getOriginalMessage())
            );
        }
    }

    /**
     * Merge JSON
     */
    private ToolResult handleMerge(String jsonStr, String json2Str) throws JsonProcessingException {
        if (json2Str == null || json2Str.isBlank()) {
            return ToolResult.failure("The merge operation requires a json2 parameter");
        }

        JsonNode node1 = objectMapper.readTree(jsonStr);
        JsonNode node2 = objectMapper.readTree(json2Str);

        if (!node1.isObject() || !node2.isObject()) {
            return ToolResult.failure("The merge operation only supports object-type JSON");
        }

        ObjectNode merged = ((ObjectNode) node1).deepCopy();
        merged.setAll((ObjectNode) node2);

        String result = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(merged);

        return ToolResult.success(
                "Merge result:\n" + result,
                Map.of("merged", objectMapper.convertValue(merged, Object.class))
        );
    }

    /**
     * Get all keys
     */
    private ToolResult handleKeys(String jsonStr) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonStr);

        if (!node.isObject()) {
            return ToolResult.failure("The keys operation only supports object-type JSON");
        }

        List<String> keys = getObjectKeys(node);

        return ToolResult.success(
                "JSON object contains " + keys.size() + " keys: " + String.join(", ", keys),
                Map.of("keys", keys, "count", keys.size())
        );
    }

    /**
     * Get all values
     */
    private ToolResult handleValues(String jsonStr) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonStr);

        if (!node.isObject()) {
            return ToolResult.failure("The values operation only supports object-type JSON");
        }

        List<Object> values = new ArrayList<>();
        node.fields().forEachRemaining(entry -> {
            values.add(objectMapper.convertValue(entry.getValue(), Object.class));
        });

        return ToolResult.success(
                "JSON object contains " + values.size() + " values",
                Map.of("values", values, "count", values.size())
        );
    }

    /**
     * Count elements
     */
    private ToolResult handleCount(String jsonStr) throws JsonProcessingException {
        JsonNode node = objectMapper.readTree(jsonStr);
        int count = node.size();
        String type = node.isArray() ? "array elements" : "object properties";

        return ToolResult.success(
                String.format("JSON contains %d %s", count, type),
                Map.of("count", count, "type", getNodeType(node))
        );
    }

    /**
     * Filter array
     */
    private ToolResult handleFilter(String jsonStr, String condition) throws JsonProcessingException {
        if (condition == null || condition.isBlank()) {
            return ToolResult.failure("The filter operation requires a condition parameter (e.g. status=active)");
        }

        JsonNode node = objectMapper.readTree(jsonStr);

        if (!node.isArray()) {
            return ToolResult.failure("The filter operation only supports array-type JSON");
        }

        // Parse condition
        String[] parts = condition.split("=", 2);
        if (parts.length != 2) {
            return ToolResult.failure("Invalid condition format, should be key=value");
        }

        String key = parts[0].trim();
        String value = parts[1].trim();

        ArrayNode filtered = objectMapper.createArrayNode();
        for (JsonNode element : node) {
            if (element.isObject()) {
                JsonNode fieldValue = getByPath(element, key);
                if (fieldValue != null && fieldValue.asText().equals(value)) {
                    filtered.add(element);
                }
            }
        }

        return ToolResult.success(
                String.format("Filter result: %d elements match condition '%s'\n%s",
                        filtered.size(), condition,
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(filtered)),
                Map.of(
                        "filtered", objectMapper.convertValue(filtered, Object.class),
                        "original_count", node.size(),
                        "filtered_count", filtered.size()
                )
        );
    }

    /**
     * Get value by path
     */
    private JsonNode getByPath(JsonNode node, String path) {
        if (path == null || path.isEmpty()) {
            return node;
        }

        String[] parts = path.split("\\.");
        JsonNode current = node;

        for (String part : parts) {
            if (current == null || current.isMissingNode()) {
                return null;
            }

            // Handle array index [n] or [*]
            if (part.contains("[")) {
                int bracketStart = part.indexOf('[');
                String fieldName = part.substring(0, bracketStart);
                String indexStr = part.substring(bracketStart + 1, part.length() - 1);

                if (!fieldName.isEmpty()) {
                    current = current.get(fieldName);
                    if (current == null) {
                        return null;
                    }
                }

                if (current.isArray()) {
                    if (indexStr.equals("*")) {
                        // Wildcard: return all elements
                        ArrayNode results = objectMapper.createArrayNode();
                        for (JsonNode element : current) {
                            results.add(element);
                        }
                        current = results;
                    } else {
                        int index = Integer.parseInt(indexStr);
                        if (index < 0) {
                            index = current.size() + index; // Negative index
                        }
                        current = current.get(index);
                    }
                }
            } else {
                current = current.get(part);
            }
        }

        return current;
    }

    /**
     * Get node type
     */
    private String getNodeType(JsonNode node) {
        if (node.isObject()) return "object";
        if (node.isArray()) return "array";
        if (node.isTextual()) return "string";
        if (node.isNumber()) return "number";
        if (node.isBoolean()) return "boolean";
        if (node.isNull()) return "null";
        return "unknown";
    }

    /**
     * Get all keys of an object
     */
    private List<String> getObjectKeys(JsonNode node) {
        List<String> keys = new ArrayList<>();
        node.fieldNames().forEachRemaining(keys::add);
        return keys;
    }

    @Override
    public String getCategory() {
        return "data";
    }
}
