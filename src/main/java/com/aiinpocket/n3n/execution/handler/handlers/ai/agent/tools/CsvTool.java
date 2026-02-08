package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * CSV processing tool
 * Supports parsing, generating, and querying CSV data
 */
@Component
@Slf4j
public class CsvTool implements AgentNodeTool {

    private static final int MAX_ROWS = 10000;
    private static final int MAX_COLUMNS = 100;

    @Override
    public String getId() {
        return "csv";
    }

    @Override
    public String getName() {
        return "CSV";
    }

    @Override
    public String getDescription() {
        return """
                CSV data processing tool, supports multiple operations:
                - parse: Parse CSV text into structured data
                - generate: Generate CSV from structured data
                - query: Query/filter CSV data
                - stats: Calculate column statistics

                Parameters:
                - data: CSV text or structured data
                - operation: Operation type
                - delimiter: Delimiter (default comma)
                - hasHeader: Whether there is a header row (default true)
                - filter: Filter condition (for query operation)
                - column: Column name (for stats operation)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "data", Map.of(
                                "type", "string",
                                "description", "CSV text data"
                        ),
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("parse", "generate", "query", "stats"),
                                "description", "Operation type",
                                "default", "parse"
                        ),
                        "delimiter", Map.of(
                                "type", "string",
                                "description", "Delimiter",
                                "default", ","
                        ),
                        "hasHeader", Map.of(
                                "type", "boolean",
                                "description", "Whether there is a header row",
                                "default", true
                        ),
                        "filter", Map.of(
                                "type", "string",
                                "description", "Filter condition (format: column=value)"
                        ),
                        "column", Map.of(
                                "type", "string",
                                "description", "Column name (for stats)"
                        )
                ),
                "required", List.of("data")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String data = (String) parameters.get("data");
                if (data == null || data.isBlank()) {
                    return ToolResult.failure("Data cannot be empty");
                }

                String operation = (String) parameters.getOrDefault("operation", "parse");
                String delimiter = (String) parameters.getOrDefault("delimiter", ",");
                boolean hasHeader = !Boolean.FALSE.equals(parameters.get("hasHeader"));

                return switch (operation) {
                    case "parse" -> parseCsv(data, delimiter, hasHeader);
                    case "query" -> queryCsv(data, delimiter, hasHeader, (String) parameters.get("filter"));
                    case "stats" -> statsCsv(data, delimiter, hasHeader, (String) parameters.get("column"));
                    default -> ToolResult.failure("Unsupported operation: " + operation);
                };

            } catch (Exception e) {
                log.error("CSV operation failed", e);
                return ToolResult.failure("CSV operation failed");
            }
        });
    }

    private ToolResult parseCsv(String data, String delimiter, boolean hasHeader) {
        List<List<String>> rows = parseRows(data, delimiter);
        if (rows.isEmpty()) {
            return ToolResult.failure("CSV data is empty");
        }

        List<String> headers;
        List<Map<String, String>> records = new ArrayList<>();

        if (hasHeader) {
            headers = rows.get(0);
            for (int i = 1; i < rows.size() && i < MAX_ROWS; i++) {
                List<String> row = rows.get(i);
                Map<String, String> record = new LinkedHashMap<>();
                for (int j = 0; j < Math.min(headers.size(), row.size()); j++) {
                    record.put(headers.get(j), row.get(j));
                }
                records.add(record);
            }
        } else {
            headers = new ArrayList<>();
            for (int i = 0; i < rows.get(0).size(); i++) {
                headers.add("column" + (i + 1));
            }
            for (List<String> row : rows) {
                Map<String, String> record = new LinkedHashMap<>();
                for (int j = 0; j < Math.min(headers.size(), row.size()); j++) {
                    record.put(headers.get(j), row.get(j));
                }
                records.add(record);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Parsing complete: %d records, %d columns\n", records.size(), headers.size()));
        sb.append("Columns: ").append(String.join(", ", headers)).append("\n\n");
        sb.append("First 5 records:\n");
        for (int i = 0; i < Math.min(5, records.size()); i++) {
            sb.append(String.format("%d. %s\n", i + 1, records.get(i)));
        }

        return ToolResult.success(sb.toString(), Map.of(
                "rowCount", records.size(),
                "columnCount", headers.size(),
                "headers", headers,
                "records", records.size() <= 100 ? records : records.subList(0, 100)
        ));
    }

    private ToolResult queryCsv(String data, String delimiter, boolean hasHeader, String filter) {
        if (filter == null || filter.isBlank()) {
            return ToolResult.failure("The query operation requires a filter parameter");
        }

        List<List<String>> rows = parseRows(data, delimiter);
        if (rows.isEmpty()) {
            return ToolResult.failure("CSV data is empty");
        }

        List<String> headers = hasHeader ? rows.get(0) : generateHeaders(rows.get(0).size());
        int startIdx = hasHeader ? 1 : 0;

        // Parse filter condition
        String[] filterParts = filter.split("=", 2);
        if (filterParts.length != 2) {
            return ToolResult.failure("Invalid filter format, should be column=value");
        }

        String filterColumn = filterParts[0].trim();
        String filterValue = filterParts[1].trim();
        int columnIdx = headers.indexOf(filterColumn);
        if (columnIdx == -1) {
            return ToolResult.failure("Column not found: " + filterColumn);
        }

        List<Map<String, String>> filtered = new ArrayList<>();
        for (int i = startIdx; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (columnIdx < row.size() && row.get(columnIdx).contains(filterValue)) {
                Map<String, String> record = new LinkedHashMap<>();
                for (int j = 0; j < Math.min(headers.size(), row.size()); j++) {
                    record.put(headers.get(j), row.get(j));
                }
                filtered.add(record);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Query result: found %d matching records\n", filtered.size()));
        sb.append(String.format("Condition: %s contains \"%s\"\n\n", filterColumn, filterValue));
        for (int i = 0; i < Math.min(10, filtered.size()); i++) {
            sb.append(String.format("%d. %s\n", i + 1, filtered.get(i)));
        }

        return ToolResult.success(sb.toString(), Map.of(
                "count", filtered.size(),
                "records", filtered
        ));
    }

    private ToolResult statsCsv(String data, String delimiter, boolean hasHeader, String column) {
        if (column == null || column.isBlank()) {
            return ToolResult.failure("The stats operation requires a column parameter");
        }

        List<List<String>> rows = parseRows(data, delimiter);
        if (rows.isEmpty()) {
            return ToolResult.failure("CSV data is empty");
        }

        List<String> headers = hasHeader ? rows.get(0) : generateHeaders(rows.get(0).size());
        int columnIdx = headers.indexOf(column);
        if (columnIdx == -1) {
            return ToolResult.failure("Column not found: " + column);
        }

        int startIdx = hasHeader ? 1 : 0;
        List<Double> numbers = new ArrayList<>();
        Map<String, Integer> valueCounts = new LinkedHashMap<>();

        for (int i = startIdx; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (columnIdx < row.size()) {
                String value = row.get(columnIdx);
                valueCounts.merge(value, 1, Integer::sum);
                try {
                    numbers.add(Double.parseDouble(value));
                } catch (NumberFormatException ignored) {}
            }
        }

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalRows", rows.size() - startIdx);
        stats.put("uniqueValues", valueCounts.size());

        if (!numbers.isEmpty()) {
            double sum = numbers.stream().mapToDouble(Double::doubleValue).sum();
            double avg = sum / numbers.size();
            double min = numbers.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double max = numbers.stream().mapToDouble(Double::doubleValue).max().orElse(0);

            stats.put("numericCount", numbers.size());
            stats.put("sum", sum);
            stats.put("average", avg);
            stats.put("min", min);
            stats.put("max", max);
        }

        // Most common values
        List<Map.Entry<String, Integer>> topValues = valueCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Column \"%s\" statistics:\n", column));
        sb.append(String.format("- Total rows: %d\n", stats.get("totalRows")));
        sb.append(String.format("- Unique values: %d\n", stats.get("uniqueValues")));

        if (stats.containsKey("numericCount")) {
            sb.append(String.format("- Numeric count: %d\n", stats.get("numericCount")));
            sb.append(String.format("- Sum: %.2f\n", stats.get("sum")));
            sb.append(String.format("- Average: %.2f\n", stats.get("average")));
            sb.append(String.format("- Min: %.2f\n", stats.get("min")));
            sb.append(String.format("- Max: %.2f\n", stats.get("max")));
        }

        sb.append("\nMost common values:\n");
        for (var entry : topValues) {
            sb.append(String.format("  - \"%s\": %d times\n", entry.getKey(), entry.getValue()));
        }

        return ToolResult.success(sb.toString(), stats);
    }

    private List<List<String>> parseRows(String data, String delimiter) {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(data))) {
            String line;
            while ((line = reader.readLine()) != null && rows.size() < MAX_ROWS) {
                List<String> cells = parseLine(line, delimiter);
                if (!cells.isEmpty()) {
                    rows.add(cells);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse CSV", e);
        }
        return rows;
    }

    private List<String> parseLine(String line, String delimiter) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length() && cells.size() < MAX_COLUMNS; i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == delimiter.charAt(0) && !inQuotes) {
                cells.add(current.toString().trim());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString().trim());
        return cells;
    }

    private List<String> generateHeaders(int count) {
        List<String> headers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            headers.add("column" + (i + 1));
        }
        return headers;
    }

    @Override
    public String getCategory() {
        return "data";
    }
}
