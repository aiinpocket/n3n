package com.aiinpocket.n3n.execution.handler.handlers.data;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.MultiOperationNodeHandler;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * JSON data manipulation node handler.
 *
 * Supports:
 * - Parse: JSON string to object
 * - Stringify: Object to JSON string
 * - Get Value: Extract value using JSONPath
 * - Set Value: Set value at path
 * - Merge: Merge multiple objects
 * - Filter: Filter array with JSONPath
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JsonNodeHandler extends MultiOperationNodeHandler {

    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return "json";
    }

    @Override
    public String getDisplayName() {
        return "JSON";
    }

    @Override
    public String getDescription() {
        return "Parse, manipulate, and transform JSON data.";
    }

    @Override
    public String getCategory() {
        return "Data";
    }

    @Override
    public String getIcon() {
        return "json";
    }

    @Override
    public String getCredentialType() {
        return null; // No credential needed
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("transform", ResourceDef.of("transform", "Transform", "Transform and manipulate JSON"));
        resources.put("query", ResourceDef.of("query", "Query", "Query and extract JSON data"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // Transform operations
        operations.put("transform", List.of(
            OperationDef.create("parse", "Parse")
                .description("Parse a JSON string into an object")
                .fields(List.of(
                    FieldDef.textarea("jsonString", "JSON String")
                        .withDescription("The JSON string to parse")
                        .withPlaceholder("{\"key\": \"value\"}")
                        .required()
                ))
                .requiresCredential(false)
                .outputDescription("Returns parsed JSON object")
                .build(),

            OperationDef.create("stringify", "Stringify")
                .description("Convert an object to a JSON string")
                .fields(List.of(
                    FieldDef.textarea("object", "Object")
                        .withDescription("The object to stringify (JSON format)")
                        .withPlaceholder("{\"key\": \"value\"}")
                        .required(),
                    FieldDef.bool("pretty", "Pretty Print")
                        .withDefault(false)
                        .withDescription("Format with indentation")
                ))
                .requiresCredential(false)
                .outputDescription("Returns JSON string")
                .build(),

            OperationDef.create("merge", "Merge Objects")
                .description("Deep merge multiple JSON objects")
                .fields(List.of(
                    FieldDef.textarea("object1", "Object 1")
                        .withDescription("First object (JSON format)")
                        .withPlaceholder("{\"a\": 1}")
                        .required(),
                    FieldDef.textarea("object2", "Object 2")
                        .withDescription("Second object (JSON format)")
                        .withPlaceholder("{\"b\": 2}")
                        .required(),
                    FieldDef.textarea("object3", "Object 3")
                        .withDescription("Third object (optional)")
                ))
                .requiresCredential(false)
                .outputDescription("Returns merged object")
                .build(),

            OperationDef.create("setValue", "Set Value")
                .description("Set a value at a specific path")
                .fields(List.of(
                    FieldDef.textarea("object", "Object")
                        .withDescription("The source object (JSON format)")
                        .withPlaceholder("{\"user\": {\"name\": \"John\"}}")
                        .required(),
                    FieldDef.string("path", "Path")
                        .withDescription("Path to set (dot notation: user.name)")
                        .withPlaceholder("user.email")
                        .required(),
                    FieldDef.textarea("value", "Value")
                        .withDescription("Value to set (JSON format)")
                        .withPlaceholder("\"john@example.com\"")
                        .required()
                ))
                .requiresCredential(false)
                .outputDescription("Returns object with value set")
                .build()
        ));

        // Query operations
        operations.put("query", List.of(
            OperationDef.create("getValue", "Get Value")
                .description("Extract a value using JSONPath")
                .fields(List.of(
                    FieldDef.textarea("object", "Object")
                        .withDescription("The object to query (JSON format)")
                        .withPlaceholder("{\"users\": [{\"name\": \"John\"}]}")
                        .required(),
                    FieldDef.string("path", "JSONPath")
                        .withDescription("JSONPath expression (e.g., $.users[0].name)")
                        .withPlaceholder("$.users[0].name")
                        .required(),
                    FieldDef.textarea("defaultValue", "Default Value")
                        .withDescription("Value if path not found (JSON format)")
                        .withPlaceholder("null")
                ))
                .requiresCredential(false)
                .outputDescription("Returns extracted value")
                .build(),

            OperationDef.create("filter", "Filter Array")
                .description("Filter an array using JSONPath")
                .fields(List.of(
                    FieldDef.textarea("array", "Array")
                        .withDescription("The array to filter (JSON format)")
                        .withPlaceholder("[{\"active\": true}, {\"active\": false}]")
                        .required(),
                    FieldDef.string("filterPath", "Filter Expression")
                        .withDescription("JSONPath filter (e.g., $[?(@.active==true)])")
                        .withPlaceholder("$[?(@.active==true)]")
                        .required()
                ))
                .requiresCredential(false)
                .outputDescription("Returns filtered array")
                .build(),

            OperationDef.create("keys", "Get Keys")
                .description("Get all keys from an object")
                .fields(List.of(
                    FieldDef.textarea("object", "Object")
                        .withDescription("The object (JSON format)")
                        .withPlaceholder("{\"a\": 1, \"b\": 2}")
                        .required()
                ))
                .requiresCredential(false)
                .outputDescription("Returns array of keys")
                .build(),

            OperationDef.create("values", "Get Values")
                .description("Get all values from an object")
                .fields(List.of(
                    FieldDef.textarea("object", "Object")
                        .withDescription("The object (JSON format)")
                        .withPlaceholder("{\"a\": 1, \"b\": 2}")
                        .required()
                ))
                .requiresCredential(false)
                .outputDescription("Returns array of values")
                .build()
        ));

        return operations;
    }

    @Override
    public NodeExecutionResult executeOperation(
        NodeExecutionContext context,
        String resource,
        String operation,
        Map<String, Object> credential,
        Map<String, Object> params
    ) {
        try {
            return switch (resource) {
                case "transform" -> switch (operation) {
                    case "parse" -> parse(params);
                    case "stringify" -> stringify(params);
                    case "merge" -> merge(params);
                    case "setValue" -> setValue(params);
                    default -> NodeExecutionResult.failure("Unknown transform operation: " + operation);
                };
                case "query" -> switch (operation) {
                    case "getValue" -> getValue(params);
                    case "filter" -> filter(params);
                    case "keys" -> getKeys(params);
                    case "values" -> getValues(params);
                    default -> NodeExecutionResult.failure("Unknown query operation: " + operation);
                };
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (Exception e) {
            log.error("JSON operation error: {}", e.getMessage());
            return NodeExecutionResult.failure("JSON error: " + e.getMessage());
        }
    }

    private NodeExecutionResult parse(Map<String, Object> params) throws JsonProcessingException {
        String jsonString = getRequiredParam(params, "jsonString");
        Object parsed = objectMapper.readValue(jsonString, Object.class);

        return NodeExecutionResult.success(Map.of(
            "data", parsed,
            "type", parsed instanceof List ? "array" : "object"
        ));
    }

    private NodeExecutionResult stringify(Map<String, Object> params) throws JsonProcessingException {
        String objectStr = getRequiredParam(params, "object");
        boolean pretty = getBoolParam(params, "pretty", false);

        Object obj = objectMapper.readValue(objectStr, Object.class);
        String result = pretty
            ? objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(obj)
            : objectMapper.writeValueAsString(obj);

        return NodeExecutionResult.success(Map.of(
            "json", result
        ));
    }

    private NodeExecutionResult merge(Map<String, Object> params) throws JsonProcessingException {
        String obj1Str = getRequiredParam(params, "object1");
        String obj2Str = getRequiredParam(params, "object2");
        String obj3Str = getParam(params, "object3", "");

        JsonNode node1 = objectMapper.readTree(obj1Str);
        JsonNode node2 = objectMapper.readTree(obj2Str);

        ObjectNode merged = (ObjectNode) deepMerge(node1, node2);

        if (!obj3Str.isEmpty()) {
            JsonNode node3 = objectMapper.readTree(obj3Str);
            merged = (ObjectNode) deepMerge(merged, node3);
        }

        Object result = objectMapper.treeToValue(merged, Object.class);
        return NodeExecutionResult.success(Map.of("data", result));
    }

    private JsonNode deepMerge(JsonNode base, JsonNode override) {
        if (!base.isObject() || !override.isObject()) {
            return override;
        }

        ObjectNode result = ((ObjectNode) base).deepCopy();
        Iterator<Map.Entry<String, JsonNode>> fields = override.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            JsonNode overrideValue = field.getValue();

            if (result.has(fieldName) && result.get(fieldName).isObject() && overrideValue.isObject()) {
                result.set(fieldName, deepMerge(result.get(fieldName), overrideValue));
            } else {
                result.set(fieldName, overrideValue);
            }
        }
        return result;
    }

    private NodeExecutionResult setValue(Map<String, Object> params) throws JsonProcessingException {
        String objectStr = getRequiredParam(params, "object");
        String path = getRequiredParam(params, "path");
        String valueStr = getRequiredParam(params, "value");

        ObjectNode node = (ObjectNode) objectMapper.readTree(objectStr);
        JsonNode valueNode = objectMapper.readTree(valueStr);

        String[] parts = path.split("\\.");
        ObjectNode current = node;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (!current.has(part) || !current.get(part).isObject()) {
                current.set(part, objectMapper.createObjectNode());
            }
            current = (ObjectNode) current.get(part);
        }

        current.set(parts[parts.length - 1], valueNode);

        Object result = objectMapper.treeToValue(node, Object.class);
        return NodeExecutionResult.success(Map.of("data", result));
    }

    private NodeExecutionResult getValue(Map<String, Object> params) throws JsonProcessingException {
        String objectStr = getRequiredParam(params, "object");
        String path = getRequiredParam(params, "path");
        String defaultValueStr = getParam(params, "defaultValue", "null");

        Object obj = objectMapper.readValue(objectStr, Object.class);
        Object defaultValue = objectMapper.readValue(defaultValueStr, Object.class);

        try {
            Object value = JsonPath.read(obj, path);
            return NodeExecutionResult.success(Map.of(
                "value", value,
                "found", true
            ));
        } catch (PathNotFoundException e) {
            return NodeExecutionResult.success(Map.of(
                "value", defaultValue,
                "found", false
            ));
        }
    }

    private NodeExecutionResult filter(Map<String, Object> params) throws JsonProcessingException {
        String arrayStr = getRequiredParam(params, "array");
        String filterPath = getRequiredParam(params, "filterPath");

        Object array = objectMapper.readValue(arrayStr, Object.class);
        Object filtered = JsonPath.read(array, filterPath);

        List<?> resultList = filtered instanceof List ? (List<?>) filtered : List.of(filtered);

        return NodeExecutionResult.success(Map.of(
            "data", resultList,
            "count", resultList.size()
        ));
    }

    private NodeExecutionResult getKeys(Map<String, Object> params) throws JsonProcessingException {
        String objectStr = getRequiredParam(params, "object");

        JsonNode node = objectMapper.readTree(objectStr);
        if (!node.isObject()) {
            return NodeExecutionResult.failure("Input must be an object");
        }

        List<String> keys = new ArrayList<>();
        node.fieldNames().forEachRemaining(keys::add);

        return NodeExecutionResult.success(Map.of(
            "keys", keys,
            "count", keys.size()
        ));
    }

    private NodeExecutionResult getValues(Map<String, Object> params) throws JsonProcessingException {
        String objectStr = getRequiredParam(params, "object");

        JsonNode node = objectMapper.readTree(objectStr);
        if (!node.isObject()) {
            return NodeExecutionResult.failure("Input must be an object");
        }

        List<Object> values = new ArrayList<>();
        node.elements().forEachRemaining(n -> {
            try {
                values.add(objectMapper.treeToValue(n, Object.class));
            } catch (JsonProcessingException e) {
                values.add(n.asText());
            }
        });

        return NodeExecutionResult.success(Map.of(
            "values", values,
            "count", values.size()
        ));
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "any")
            )
        );
    }
}
