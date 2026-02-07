package com.aiinpocket.n3n.execution.handler.handlers.file;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Handler for converting data between file formats.
 *
 * Operations:
 * - csvToJson: Convert CSV text to JSON array of objects
 * - jsonToCsv: Convert JSON array of objects to CSV text
 * - textToBase64: Encode text as Base64
 * - base64ToText: Decode Base64 to text
 */
@Component
@Slf4j
public class ConvertFileNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "convertFile";
    }

    @Override
    public String getDisplayName() {
        return "Convert File";
    }

    @Override
    public String getDescription() {
        return "Convert data between formats: CSV to JSON, JSON to CSV, text to Base64, and Base64 to text.";
    }

    @Override
    public String getCategory() {
        return "Files";
    }

    @Override
    public String getIcon() {
        return "swap";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String operation = getStringConfig(context, "operation", "csvToJson");
        String input = getStringConfig(context, "input", "");

        // If input is empty, try from input data
        if (input.isEmpty() && context.getInputData() != null) {
            Object data = context.getInputData().get("data");
            if (data == null) data = context.getInputData().get("input");
            if (data != null) input = data.toString();
        }

        if (input.isEmpty()) {
            return NodeExecutionResult.failure("Input data is required");
        }

        try {
            Map<String, Object> output = switch (operation) {
                case "csvToJson" -> csvToJson(input, context);
                case "jsonToCsv" -> jsonToCsv(input, context);
                case "textToBase64" -> textToBase64(input);
                case "base64ToText" -> base64ToText(input);
                default -> null;
            };

            if (output == null) {
                return NodeExecutionResult.failure("Unknown operation: " + operation);
            }

            return NodeExecutionResult.builder()
                .success(true)
                .output(output)
                .build();

        } catch (Exception e) {
            log.error("Convert operation failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Convert operation failed: " + e.getMessage());
        }
    }

    private Map<String, Object> csvToJson(String csvText, NodeExecutionContext context) {
        String delimiter = getStringConfig(context, "delimiter", ",");
        boolean hasHeader = getBooleanConfig(context, "hasHeader", true);

        String[] lines = csvText.split("\\r?\\n");
        if (lines.length == 0) {
            Map<String, Object> output = new HashMap<>();
            output.put("data", Collections.emptyList());
            output.put("rowCount", 0);
            return output;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        String[] headers = null;
        int startIndex = 0;

        if (hasHeader) {
            headers = parseCsvLine(lines[0], delimiter);
            startIndex = 1;
        }

        for (int i = startIndex; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] values = parseCsvLine(line, delimiter);
            Map<String, Object> row = new LinkedHashMap<>();

            if (headers != null) {
                for (int j = 0; j < headers.length; j++) {
                    String value = j < values.length ? values[j].trim() : "";
                    row.put(headers[j].trim(), inferType(value));
                }
            } else {
                for (int j = 0; j < values.length; j++) {
                    row.put("column_" + j, inferType(values[j].trim()));
                }
            }

            rows.add(row);
        }

        Map<String, Object> output = new HashMap<>();
        output.put("data", rows);
        output.put("rowCount", rows.size());
        if (headers != null) {
            output.put("headers", Arrays.asList(headers));
        }
        return output;
    }

    private Map<String, Object> jsonToCsv(String jsonText, NodeExecutionContext context) {
        String delimiter = getStringConfig(context, "delimiter", ",");
        boolean includeHeader = getBooleanConfig(context, "includeHeader", true);

        String trimmed = jsonText.trim();
        if (!trimmed.startsWith("[")) {
            return Map.of("data", "", "rowCount", 0);
        }

        // Parse JSON array of objects
        List<Map<String, String>> rows = parseJsonArrayOfObjects(trimmed);

        if (rows.isEmpty()) {
            Map<String, Object> output = new HashMap<>();
            output.put("data", "");
            output.put("rowCount", 0);
            return output;
        }

        // Collect all unique headers preserving order
        LinkedHashSet<String> headerSet = new LinkedHashSet<>();
        for (Map<String, String> row : rows) {
            headerSet.addAll(row.keySet());
        }
        List<String> headers = new ArrayList<>(headerSet);

        StringBuilder csv = new StringBuilder();

        if (includeHeader) {
            csv.append(String.join(delimiter, headers));
            csv.append("\n");
        }

        for (Map<String, String> row : rows) {
            List<String> values = new ArrayList<>();
            for (String header : headers) {
                String value = row.getOrDefault(header, "");
                if (value.contains(delimiter) || value.contains("\"") || value.contains("\n")) {
                    value = "\"" + value.replace("\"", "\"\"") + "\"";
                }
                values.add(value);
            }
            csv.append(String.join(delimiter, values));
            csv.append("\n");
        }

        Map<String, Object> output = new HashMap<>();
        output.put("data", csv.toString());
        output.put("rowCount", rows.size());
        output.put("headers", headers);
        return output;
    }

    private Map<String, Object> textToBase64(String text) {
        String encoded = Base64.getEncoder().encodeToString(
            text.getBytes(StandardCharsets.UTF_8));

        Map<String, Object> output = new HashMap<>();
        output.put("data", encoded);
        output.put("originalSize", text.length());
        output.put("encodedSize", encoded.length());
        return output;
    }

    private Map<String, Object> base64ToText(String base64) {
        byte[] decoded = Base64.getDecoder().decode(base64.trim());
        String text = new String(decoded, StandardCharsets.UTF_8);

        Map<String, Object> output = new HashMap<>();
        output.put("data", text);
        output.put("decodedSize", text.length());
        return output;
    }

    /**
     * Infer the type of a CSV value (number, boolean, null, or string).
     */
    private Object inferType(String value) {
        if (value.isEmpty() || value.equalsIgnoreCase("null")) return null;
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;

        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    /**
     * Simple CSV line parser that handles quoted fields.
     */
    private String[] parseCsvLine(String line, String delimiter) {
        List<String> fields = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();
        char delimChar = delimiter.charAt(0);

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimChar && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());

        return fields.toArray(new String[0]);
    }

    /**
     * Parse a JSON array of flat objects into a list of string maps.
     */
    private List<Map<String, String>> parseJsonArrayOfObjects(String json) {
        List<Map<String, String>> result = new ArrayList<>();

        json = json.trim();
        if (!json.startsWith("[") || !json.endsWith("]")) return result;

        String inner = json.substring(1, json.length() - 1).trim();
        if (inner.isEmpty()) return result;

        List<String> objects = splitJsonElements(inner);

        for (String objStr : objects) {
            objStr = objStr.trim();
            if (!objStr.startsWith("{") || !objStr.endsWith("}")) continue;

            Map<String, String> row = new LinkedHashMap<>();
            String objInner = objStr.substring(1, objStr.length() - 1).trim();
            List<String> pairs = splitJsonElements(objInner);

            for (String pair : pairs) {
                int colonIdx = findUnquotedColon(pair);
                if (colonIdx < 0) continue;

                String key = pair.substring(0, colonIdx).trim();
                String value = pair.substring(colonIdx + 1).trim();

                key = unquote(key);
                value = unquote(value);
                if (value.equals("null")) value = "";

                row.put(key, value);
            }

            if (!row.isEmpty()) result.add(row);
        }

        return result;
    }

    private List<String> splitJsonElements(String inner) {
        List<String> items = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\' && inString) {
                current.append(c);
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                current.append(c);
                continue;
            }
            if (inString) {
                current.append(c);
                continue;
            }
            if (c == '{' || c == '[') depth++;
            if (c == '}' || c == ']') depth--;
            if (c == ',' && depth == 0) {
                items.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) items.add(last);
        return items;
    }

    private int findUnquotedColon(String s) {
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && inString) { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (c == ':' && !inString) return i;
        }
        return -1;
    }

    private String unquote(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "operation", Map.of(
                    "type", "string",
                    "title", "Operation",
                    "enum", List.of("csvToJson", "jsonToCsv", "textToBase64", "base64ToText"),
                    "default", "csvToJson"
                ),
                "input", Map.of(
                    "type", "string",
                    "title", "Input",
                    "description", "Input data to convert"
                ),
                "delimiter", Map.of(
                    "type", "string",
                    "title", "CSV Delimiter",
                    "description", "Delimiter for CSV operations",
                    "default", ","
                ),
                "hasHeader", Map.of(
                    "type", "boolean",
                    "title", "Has Header Row",
                    "description", "Whether CSV input has a header row",
                    "default", true
                ),
                "includeHeader", Map.of(
                    "type", "boolean",
                    "title", "Include Header",
                    "description", "Include header row in CSV output",
                    "default", true
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "data", "type", "any", "required", true)
            ),
            "outputs", List.of(
                Map.of("name", "data", "type", "any"),
                Map.of("name", "rowCount", "type", "number")
            )
        );
    }
}
