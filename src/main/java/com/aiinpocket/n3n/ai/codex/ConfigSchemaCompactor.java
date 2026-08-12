package com.aiinpocket.n3n.ai.codex;

import java.util.List;
import java.util.Map;

/**
 * Compacts a node's JSON-Schema-like configSchema map into a short,
 * deterministic, prompt-friendly text block (one line per field).
 *
 * Output format per field:
 *   - fieldName (type, required) [allowed: a|b|c]: description
 *
 * Supports both plain JSON-Schema ("properties"/"required") and the
 * multi-operation extension ("x-operation-definitions" with field lists).
 * Output is bounded to MAX_CHARS and never cuts a line mid-way.
 */
public final class ConfigSchemaCompactor {

    private static final int MAX_CHARS = 600;
    private static final int MAX_ENUM_VALUES = 8;
    private static final int MAX_DESC_CHARS = 60;

    private ConfigSchemaCompactor() {
    }

    /**
     * Render a compact field summary from a configSchema map.
     * Returns an empty string when there is nothing meaningful to show.
     */
    public static String compact(Map<String, Object> configSchema) {
        if (configSchema == null || configSchema.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        if (configSchema.get("x-operation-definitions") instanceof List<?> operations) {
            appendOperationFields(sb, operations);
        } else if (configSchema.get("properties") instanceof Map<?, ?> properties) {
            appendJsonSchemaFields(sb, properties, requiredNames(configSchema));
        }

        return sb.toString();
    }

    private static List<?> requiredNames(Map<String, Object> schema) {
        return schema.get("required") instanceof List<?> required ? required : List.of();
    }

    private static void appendJsonSchemaFields(
            StringBuilder sb, Map<?, ?> properties, List<?> requiredNames) {
        for (Map.Entry<?, ?> entry : properties.entrySet()) {
            String name = String.valueOf(entry.getKey());
            if (!(entry.getValue() instanceof Map<?, ?> prop)) {
                continue;
            }
            String line = fieldLine(
                    name,
                    stringValue(prop.get("type"), "string"),
                    requiredNames.contains(name),
                    prop.get("enum") instanceof List<?> e ? e : null,
                    stringValue(prop.get("description"), null),
                    prop.get("default"));
            if (!appendBounded(sb, line)) {
                return;
            }
        }
    }

    private static void appendOperationFields(StringBuilder sb, List<?> operations) {
        for (Object opObj : operations) {
            if (!(opObj instanceof Map<?, ?> op)) {
                continue;
            }
            String opHeader = "operation=" + stringValue(op.get("name"), "?")
                    + " (resource=" + stringValue(op.get("resource"), "?") + "):";
            if (!appendBounded(sb, opHeader)) {
                return;
            }
            if (!(op.get("fields") instanceof List<?> fields)) {
                continue;
            }
            for (Object fieldObj : fields) {
                if (!(fieldObj instanceof Map<?, ?> field)) {
                    continue;
                }
                String line = fieldLine(
                        stringValue(field.get("name"), "?"),
                        stringValue(field.get("type"), "string"),
                        Boolean.TRUE.equals(field.get("required")),
                        field.get("options") instanceof List<?> o ? o : null,
                        stringValue(field.get("description"), null),
                        field.get("default"));
                if (!appendBounded(sb, line)) {
                    return;
                }
            }
        }
    }

    private static String fieldLine(String name, String type, boolean required,
                                    List<?> allowedValues, String description, Object defaultValue) {
        StringBuilder line = new StringBuilder();
        line.append("- ").append(name)
                .append(" (").append(type).append(required ? ", required" : "").append(")");

        if (allowedValues != null && !allowedValues.isEmpty()) {
            List<?> shown = allowedValues.size() > MAX_ENUM_VALUES
                    ? allowedValues.subList(0, MAX_ENUM_VALUES)
                    : allowedValues;
            line.append(" [allowed: ");
            for (int i = 0; i < shown.size(); i++) {
                if (i > 0) {
                    line.append("|");
                }
                line.append(shown.get(i));
            }
            if (allowedValues.size() > MAX_ENUM_VALUES) {
                line.append("|...");
            }
            line.append("]");
        }

        if (defaultValue != null && isScalar(defaultValue)) {
            line.append(" (default: ").append(defaultValue).append(")");
        }

        if (description != null && !description.isBlank()) {
            String desc = description.length() > MAX_DESC_CHARS
                    ? description.substring(0, MAX_DESC_CHARS) + "…"
                    : description;
            line.append(": ").append(desc);
        }

        return line.toString();
    }

    /**
     * Appends a whole line if it fits within MAX_CHARS.
     * Returns false (and appends nothing) once the budget is exhausted.
     */
    private static boolean appendBounded(StringBuilder sb, String line) {
        if (sb.length() + line.length() + 1 > MAX_CHARS) {
            return false;
        }
        sb.append(line).append("\n");
        return true;
    }

    private static boolean isScalar(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    private static String stringValue(Object value, String fallback) {
        return value instanceof String s && !s.isBlank() ? s : fallback;
    }
}
