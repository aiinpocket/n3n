package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Handler for set fields nodes.
 * Sets or modifies data fields with new values.
 */
@Component
@Slf4j
public class SetFieldsNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "setFields";
    }

    @Override
    public String getDisplayName() {
        return "Edit Fields (Set)";
    }

    @Override
    public String getDescription() {
        return "Sets or modifies data fields with specified values.";
    }

    @Override
    public String getCategory() {
        return "Data Transform";
    }

    @Override
    public String getIcon() {
        return "edit";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        Map<String, Object> inputData = context.getInputData();
        String mode = getStringConfig(context, "mode", "add");
        boolean keepOriginal = getBooleanConfig(context, "keepOriginal", true);

        // Get fields to set from config
        Object fieldsConfig = context.getNodeConfig().get("fields");
        Map<String, Object> fields = new HashMap<>();

        if (fieldsConfig instanceof Map) {
            fields = (Map<String, Object>) fieldsConfig;
        } else if (fieldsConfig instanceof List) {
            // Handle array of {name, value} objects
            for (Object item : (List<?>) fieldsConfig) {
                if (item instanceof Map) {
                    Map<String, Object> fieldDef = (Map<String, Object>) item;
                    String name = (String) fieldDef.get("name");
                    Object value = fieldDef.get("value");
                    if (name != null) {
                        fields.put(name, value);
                    }
                }
            }
        }

        log.debug("Setting {} fields in mode: {}", fields.size(), mode);

        Map<String, Object> output;

        switch (mode) {
            case "replace":
                // Only include specified fields
                output = new LinkedHashMap<>(fields);
                break;

            case "add":
                // Add fields to existing data (don't overwrite)
                output = keepOriginal && inputData != null ? new LinkedHashMap<>(inputData) : new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : fields.entrySet()) {
                    if (!output.containsKey(entry.getKey())) {
                        output.put(entry.getKey(), processValue(entry.getValue(), inputData));
                    }
                }
                break;

            case "update":
            default:
                // Update existing data with new fields
                output = keepOriginal && inputData != null ? new LinkedHashMap<>(inputData) : new LinkedHashMap<>();
                for (Map.Entry<String, Object> entry : fields.entrySet()) {
                    output.put(entry.getKey(), processValue(entry.getValue(), inputData));
                }
                break;
        }

        return NodeExecutionResult.builder()
            .success(true)
            .output(output)
            .build();
    }

    @SuppressWarnings("unchecked")
    private Object processValue(Object value, Map<String, Object> inputData) {
        if (value instanceof String) {
            String strValue = (String) value;
            // Check for expression syntax {{expression}}
            if (strValue.startsWith("{{") && strValue.endsWith("}}")) {
                String expr = strValue.substring(2, strValue.length() - 2).trim();
                return evaluateExpression(expr, inputData);
            }
        }
        return value;
    }

    private Object evaluateExpression(String expr, Map<String, Object> inputData) {
        if (inputData == null) {
            return null;
        }

        // Simple path evaluation (e.g., "data.name")
        if (expr.startsWith("$input.") || expr.startsWith("input.")) {
            String path = expr.startsWith("$") ? expr.substring(7) : expr.substring(6);
            return getNestedValue(inputData, path);
        }

        // Direct field reference
        return getNestedValue(inputData, expr);
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
                "mode", Map.of(
                    "type", "string",
                    "title", "Mode",
                    "enum", List.of("update", "add", "replace"),
                    "enumNames", List.of(
                        "Update (overwrite existing)",
                        "Add (only add new fields)",
                        "Replace (only keep specified fields)"
                    ),
                    "default", "update"
                ),
                "keepOriginal", Map.of(
                    "type", "boolean",
                    "title", "Keep Original Data",
                    "description", "Keep original input data fields",
                    "default", true
                ),
                "fields", Map.of(
                    "type", "array",
                    "title", "Fields",
                    "description", "Fields to set or modify",
                    "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "name", Map.of("type", "string", "title", "Field Name"),
                            "value", Map.of("type", "string", "title", "Value")
                        )
                    )
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "object")
            )
        );
    }
}
