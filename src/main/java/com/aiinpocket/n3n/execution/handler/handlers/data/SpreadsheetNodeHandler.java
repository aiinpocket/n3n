package com.aiinpocket.n3n.execution.handler.handlers.data;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handler for spreadsheet/CSV data operations.
 * Parses and generates CSV data, converts between JSON and tabular formats.
 */
@Component
@Slf4j
public class SpreadsheetNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "spreadsheet";
    }

    @Override
    public String getDisplayName() {
        return "Spreadsheet / CSV";
    }

    @Override
    public String getDescription() {
        return "Parse and generate CSV data, convert between JSON and tabular formats.";
    }

    @Override
    public String getCategory() {
        return NodeCategory.DATA_TRANSFORM;
    }

    @Override
    public String getIcon() {
        return "table";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String operation = getStringConfig(context, "operation", "csvToJson");

        try {
            return switch (operation) {
                case "csvToJson" -> csvToJson(context);
                case "jsonToCsv" -> jsonToCsv(context);
                case "transpose" -> transpose(context);
                case "pivot" -> pivot(context);
                case "unpivot" -> unpivot(context);
                case "addColumn" -> addColumn(context);
                case "removeColumn" -> removeColumn(context);
                case "renameColumn" -> renameColumn(context);
                default -> NodeExecutionResult.failure("Unknown operation: " + operation);
            };
        } catch (Exception e) {
            return NodeExecutionResult.failure("Spreadsheet operation failed: " + e.getMessage());
        }
    }

    private NodeExecutionResult csvToJson(NodeExecutionContext context) {
        String csv = getStringConfig(context, "csv", "");
        if (csv.isEmpty() && context.getInputData() != null) {
            Object data = context.getInputData().get("data");
            if (data == null) data = context.getInputData().get("csv");
            if (data == null) data = context.getInputData().get("input");
            if (data != null) csv = data.toString();
        }

        if (csv.isEmpty()) {
            return NodeExecutionResult.failure("CSV data is required.");
        }

        String delimiter = getStringConfig(context, "delimiter", ",");
        boolean hasHeader = getBooleanConfig(context, "hasHeader", true);

        List<String[]> rows = parseCsv(csv, delimiter.charAt(0));
        if (rows.isEmpty()) {
            return NodeExecutionResult.success(Map.of("items", List.of(), "count", 0));
        }

        List<Map<String, Object>> items = new ArrayList<>();

        if (hasHeader && rows.size() > 1) {
            String[] headers = rows.get(0);
            for (int i = 1; i < rows.size(); i++) {
                Map<String, Object> item = new LinkedHashMap<>();
                String[] values = rows.get(i);
                for (int j = 0; j < headers.length; j++) {
                    item.put(headers[j].trim(),
                        j < values.length ? parseValue(values[j].trim()) : null);
                }
                items.add(item);
            }
        } else {
            for (String[] row : rows) {
                Map<String, Object> item = new LinkedHashMap<>();
                for (int j = 0; j < row.length; j++) {
                    item.put("column" + j, parseValue(row[j].trim()));
                }
                items.add(item);
            }
        }

        return NodeExecutionResult.success(Map.of("items", items, "count", items.size()));
    }

    @SuppressWarnings("unchecked")
    private NodeExecutionResult jsonToCsv(NodeExecutionContext context) {
        List<Map<String, Object>> items = null;

        if (context.getInputData() != null) {
            Object data = context.getInputData().get("items");
            if (data == null) data = context.getInputData().get("data");
            if (data instanceof List) {
                items = (List<Map<String, Object>>) data;
            }
        }

        if (items == null) {
            Object configItems = context.getNodeConfig().get("items");
            if (configItems instanceof List) {
                items = (List<Map<String, Object>>) configItems;
            }
        }

        if (items == null || items.isEmpty()) {
            return NodeExecutionResult.success(Map.of("csv", "", "count", 0));
        }

        String delimiter = getStringConfig(context, "delimiter", ",");
        boolean includeHeader = getBooleanConfig(context, "includeHeader", true);

        // Collect all unique keys to use as headers
        LinkedHashSet<String> headers = new LinkedHashSet<>();
        for (Map<String, Object> item : items) {
            headers.addAll(item.keySet());
        }

        StringBuilder sb = new StringBuilder();
        if (includeHeader) {
            sb.append(String.join(delimiter, headers)).append("\n");
        }

        for (Map<String, Object> item : items) {
            List<String> values = new ArrayList<>();
            for (String header : headers) {
                Object val = item.get(header);
                values.add(escapeCsvValue(val != null ? val.toString() : "", delimiter.charAt(0)));
            }
            sb.append(String.join(delimiter, values)).append("\n");
        }

        return NodeExecutionResult.success(Map.of(
            "csv", sb.toString().trim(),
            "count", items.size(),
            "columns", new ArrayList<>(headers)
        ));
    }

    @SuppressWarnings("unchecked")
    private NodeExecutionResult transpose(NodeExecutionContext context) {
        List<Map<String, Object>> items = extractItemsFromInput(context);
        if (items == null || items.isEmpty()) {
            return NodeExecutionResult.success(Map.of("items", List.of()));
        }

        // Get all keys
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (Map<String, Object> item : items) keys.addAll(item.keySet());

        List<Map<String, Object>> transposed = new ArrayList<>();
        for (String key : keys) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("field", key);
            for (int i = 0; i < items.size(); i++) {
                row.put("row" + i, items.get(i).get(key));
            }
            transposed.add(row);
        }

        return NodeExecutionResult.success(Map.of("items", transposed, "count", transposed.size()));
    }

    @SuppressWarnings("unchecked")
    private NodeExecutionResult pivot(NodeExecutionContext context) {
        List<Map<String, Object>> items = extractItemsFromInput(context);
        String rowField = getStringConfig(context, "rowField", "");
        String columnField = getStringConfig(context, "columnField", "");
        String valueField = getStringConfig(context, "valueField", "");

        if (items == null || rowField.isEmpty() || columnField.isEmpty() || valueField.isEmpty()) {
            return NodeExecutionResult.failure("Items, rowField, columnField, and valueField are required for pivot.");
        }

        Map<Object, Map<String, Object>> pivoted = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            Object rowKey = item.get(rowField);
            Object colKey = item.get(columnField);
            Object value = item.get(valueField);

            if (rowKey == null) continue;

            pivoted.computeIfAbsent(rowKey, k -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put(rowField, rowKey);
                return row;
            }).put(colKey != null ? colKey.toString() : "null", value);
        }

        List<Map<String, Object>> result = new ArrayList<>(pivoted.values());
        return NodeExecutionResult.success(Map.of("items", result, "count", result.size()));
    }

    @SuppressWarnings("unchecked")
    private NodeExecutionResult unpivot(NodeExecutionContext context) {
        List<Map<String, Object>> items = extractItemsFromInput(context);
        String idField = getStringConfig(context, "idField", "id");

        if (items == null || items.isEmpty()) {
            return NodeExecutionResult.success(Map.of("items", List.of()));
        }

        List<Map<String, Object>> unpivoted = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Object id = item.get(idField);
            for (Map.Entry<String, Object> entry : item.entrySet()) {
                if (!entry.getKey().equals(idField)) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put(idField, id);
                    row.put("field", entry.getKey());
                    row.put("value", entry.getValue());
                    unpivoted.add(row);
                }
            }
        }

        return NodeExecutionResult.success(Map.of("items", unpivoted, "count", unpivoted.size()));
    }

    @SuppressWarnings("unchecked")
    private NodeExecutionResult addColumn(NodeExecutionContext context) {
        List<Map<String, Object>> items = extractItemsFromInput(context);
        String columnName = getStringConfig(context, "columnName", "");
        String defaultValue = getStringConfig(context, "defaultValue", "");

        if (items == null || columnName.isEmpty()) {
            return NodeExecutionResult.failure("Items and columnName are required.");
        }

        for (Map<String, Object> item : items) {
            if (!item.containsKey(columnName)) {
                item.put(columnName, parseValue(defaultValue));
            }
        }

        return NodeExecutionResult.success(Map.of("items", items, "count", items.size()));
    }

    @SuppressWarnings("unchecked")
    private NodeExecutionResult removeColumn(NodeExecutionContext context) {
        List<Map<String, Object>> items = extractItemsFromInput(context);
        String columnName = getStringConfig(context, "columnName", "");

        if (items == null || columnName.isEmpty()) {
            return NodeExecutionResult.failure("Items and columnName are required.");
        }

        for (Map<String, Object> item : items) {
            item.remove(columnName);
        }

        return NodeExecutionResult.success(Map.of("items", items, "count", items.size()));
    }

    @SuppressWarnings("unchecked")
    private NodeExecutionResult renameColumn(NodeExecutionContext context) {
        List<Map<String, Object>> items = extractItemsFromInput(context);
        String oldName = getStringConfig(context, "oldName", "");
        String newName = getStringConfig(context, "newName", "");

        if (items == null || oldName.isEmpty() || newName.isEmpty()) {
            return NodeExecutionResult.failure("Items, oldName, and newName are required.");
        }

        for (Map<String, Object> item : items) {
            if (item.containsKey(oldName)) {
                item.put(newName, item.remove(oldName));
            }
        }

        return NodeExecutionResult.success(Map.of("items", items, "count", items.size()));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractItemsFromInput(NodeExecutionContext context) {
        if (context.getInputData() == null) return null;
        Object data = context.getInputData().get("items");
        if (data == null) data = context.getInputData().get("data");
        if (data instanceof List) return new ArrayList<>((List<Map<String, Object>>) data);
        return null;
    }

    private List<String[]> parseCsv(String csv, char delimiter) {
        List<String[]> result = new ArrayList<>();
        String[] lines = csv.split("\n");

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            result.add(parseCsvLine(line, delimiter));
        }

        return result;
    }

    private String[] parseCsvLine(String line, char delimiter) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                fields.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());

        return fields.toArray(new String[0]);
    }

    private String escapeCsvValue(String value, char delimiter) {
        if (value.contains(String.valueOf(delimiter)) || value.contains("\"") ||
            value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private Object parseValue(String value) {
        if (value == null || value.isEmpty()) return value;
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(value); } catch (NumberFormatException ignored) {}
        if ("true".equalsIgnoreCase(value)) return true;
        if ("false".equalsIgnoreCase(value)) return false;
        return value;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("operation", Map.of(
            "type", "string", "title", "Operation",
            "enum", List.of("csvToJson", "jsonToCsv", "transpose", "pivot", "unpivot",
                           "addColumn", "removeColumn", "renameColumn"),
            "default", "csvToJson"
        ));
        properties.put("csv", Map.of("type", "string", "title", "CSV Data"));
        properties.put("delimiter", Map.of(
            "type", "string", "title", "Delimiter", "default", ","
        ));
        properties.put("hasHeader", Map.of(
            "type", "boolean", "title", "Has Header Row", "default", true
        ));
        properties.put("includeHeader", Map.of(
            "type", "boolean", "title", "Include Header", "default", true
        ));
        properties.put("columnName", Map.of("type", "string", "title", "Column Name"));
        properties.put("defaultValue", Map.of("type", "string", "title", "Default Value"));
        properties.put("oldName", Map.of("type", "string", "title", "Old Column Name"));
        properties.put("newName", Map.of("type", "string", "title", "New Column Name"));
        properties.put("rowField", Map.of("type", "string", "title", "Row Field"));
        properties.put("columnField", Map.of("type", "string", "title", "Column Field"));
        properties.put("valueField", Map.of("type", "string", "title", "Value Field"));
        properties.put("idField", Map.of("type", "string", "title", "ID Field", "default", "id"));

        return Map.of(
            "type", "object",
            "properties", properties,
            "required", List.of("operation")
        );
    }
}
