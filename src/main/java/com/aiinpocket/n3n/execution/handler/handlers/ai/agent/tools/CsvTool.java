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
 * CSV 處理工具
 * 支援解析、生成、查詢 CSV 資料
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
                CSV 資料處理工具，支援多種操作：
                - parse: 解析 CSV 文字為結構化資料
                - generate: 從結構化資料生成 CSV
                - query: 查詢/過濾 CSV 資料
                - stats: 計算欄位統計資訊

                參數：
                - data: CSV 文字或結構化資料
                - operation: 操作類型
                - delimiter: 分隔符（預設逗號）
                - hasHeader: 是否有標題行（預設 true）
                - filter: 過濾條件（用於 query 操作）
                - column: 欄位名稱（用於 stats 操作）
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "data", Map.of(
                                "type", "string",
                                "description", "CSV 文字資料"
                        ),
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("parse", "generate", "query", "stats"),
                                "description", "操作類型",
                                "default", "parse"
                        ),
                        "delimiter", Map.of(
                                "type", "string",
                                "description", "分隔符",
                                "default", ","
                        ),
                        "hasHeader", Map.of(
                                "type", "boolean",
                                "description", "是否有標題行",
                                "default", true
                        ),
                        "filter", Map.of(
                                "type", "string",
                                "description", "過濾條件（格式：column=value）"
                        ),
                        "column", Map.of(
                                "type", "string",
                                "description", "欄位名稱（用於 stats）"
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
                    return ToolResult.failure("資料不能為空");
                }

                String operation = (String) parameters.getOrDefault("operation", "parse");
                String delimiter = (String) parameters.getOrDefault("delimiter", ",");
                boolean hasHeader = !Boolean.FALSE.equals(parameters.get("hasHeader"));

                return switch (operation) {
                    case "parse" -> parseCsv(data, delimiter, hasHeader);
                    case "query" -> queryCsv(data, delimiter, hasHeader, (String) parameters.get("filter"));
                    case "stats" -> statsCsv(data, delimiter, hasHeader, (String) parameters.get("column"));
                    default -> ToolResult.failure("不支援的操作: " + operation);
                };

            } catch (Exception e) {
                log.error("CSV operation failed", e);
                return ToolResult.failure("CSV 操作失敗: " + e.getMessage());
            }
        });
    }

    private ToolResult parseCsv(String data, String delimiter, boolean hasHeader) {
        List<List<String>> rows = parseRows(data, delimiter);
        if (rows.isEmpty()) {
            return ToolResult.failure("CSV 資料為空");
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
        sb.append(String.format("解析完成：%d 筆記錄，%d 個欄位\n", records.size(), headers.size()));
        sb.append("欄位：").append(String.join(", ", headers)).append("\n\n");
        sb.append("前 5 筆資料：\n");
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
            return ToolResult.failure("query 操作需要提供 filter 參數");
        }

        List<List<String>> rows = parseRows(data, delimiter);
        if (rows.isEmpty()) {
            return ToolResult.failure("CSV 資料為空");
        }

        List<String> headers = hasHeader ? rows.get(0) : generateHeaders(rows.get(0).size());
        int startIdx = hasHeader ? 1 : 0;

        // 解析過濾條件
        String[] filterParts = filter.split("=", 2);
        if (filterParts.length != 2) {
            return ToolResult.failure("無效的過濾條件格式，應為 column=value");
        }

        String filterColumn = filterParts[0].trim();
        String filterValue = filterParts[1].trim();
        int columnIdx = headers.indexOf(filterColumn);
        if (columnIdx == -1) {
            return ToolResult.failure("找不到欄位: " + filterColumn);
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
        sb.append(String.format("查詢結果：找到 %d 筆符合條件的記錄\n", filtered.size()));
        sb.append(String.format("條件：%s 包含 \"%s\"\n\n", filterColumn, filterValue));
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
            return ToolResult.failure("stats 操作需要提供 column 參數");
        }

        List<List<String>> rows = parseRows(data, delimiter);
        if (rows.isEmpty()) {
            return ToolResult.failure("CSV 資料為空");
        }

        List<String> headers = hasHeader ? rows.get(0) : generateHeaders(rows.get(0).size());
        int columnIdx = headers.indexOf(column);
        if (columnIdx == -1) {
            return ToolResult.failure("找不到欄位: " + column);
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

        // 最常見的值
        List<Map.Entry<String, Integer>> topValues = valueCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toList());

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("欄位 \"%s\" 統計資訊：\n", column));
        sb.append(String.format("- 總行數: %d\n", stats.get("totalRows")));
        sb.append(String.format("- 唯一值數量: %d\n", stats.get("uniqueValues")));

        if (stats.containsKey("numericCount")) {
            sb.append(String.format("- 數值數量: %d\n", stats.get("numericCount")));
            sb.append(String.format("- 總和: %.2f\n", stats.get("sum")));
            sb.append(String.format("- 平均: %.2f\n", stats.get("average")));
            sb.append(String.format("- 最小: %.2f\n", stats.get("min")));
            sb.append(String.format("- 最大: %.2f\n", stats.get("max")));
        }

        sb.append("\n最常見的值：\n");
        for (var entry : topValues) {
            sb.append(String.format("  - \"%s\": %d 次\n", entry.getKey(), entry.getValue()));
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
