package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Cron 表達式工具
 * 解析和說明 Cron 表達式
 */
@Component
@Slf4j
public class CronTool implements AgentNodeTool {

    @Override
    public String getId() {
        return "cron";
    }

    @Override
    public String getName() {
        return "Cron Expression";
    }

    @Override
    public String getDescription() {
        return """
                Cron 表達式工具，支援以下操作：
                - explain: 解釋 Cron 表達式的含義
                - next: 計算下 N 次執行時間
                - validate: 驗證 Cron 表達式格式

                支援標準 5 欄位格式（分 時 日 月 週）和 6 欄位格式（秒 分 時 日 月 週）。

                參數：
                - expression: Cron 表達式
                - operation: 操作類型（預設 explain）
                - count: 計算下幾次執行（用於 next，預設 5）
                - timezone: 時區（預設系統時區）

                範例表達式：
                - "0 0 * * *" - 每天午夜
                - "*/15 * * * *" - 每 15 分鐘
                - "0 9 * * 1-5" - 週一至週五早上 9 點
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "expression", Map.of(
                                "type", "string",
                                "description", "Cron 表達式"
                        ),
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("explain", "next", "validate"),
                                "description", "操作類型",
                                "default", "explain"
                        ),
                        "count", Map.of(
                                "type", "integer",
                                "description", "計算下幾次執行",
                                "default", 5
                        ),
                        "timezone", Map.of(
                                "type", "string",
                                "description", "時區",
                                "default", "Asia/Taipei"
                        )
                ),
                "required", List.of("expression")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String expression = (String) parameters.get("expression");
                String operation = (String) parameters.getOrDefault("operation", "explain");
                int count = Math.min(20, Math.max(1,
                        parameters.containsKey("count") ? ((Number) parameters.get("count")).intValue() : 5));
                String timezone = (String) parameters.getOrDefault("timezone", "Asia/Taipei");

                if (expression == null || expression.isBlank()) {
                    return ToolResult.failure("Cron 表達式不能為空");
                }

                // Security: limit expression length
                if (expression.length() > 100) {
                    return ToolResult.failure("表達式過長");
                }

                return switch (operation) {
                    case "explain" -> explain(expression);
                    case "next" -> nextExecutions(expression, count, timezone);
                    case "validate" -> validate(expression);
                    default -> ToolResult.failure("不支援的操作: " + operation);
                };

            } catch (Exception e) {
                log.error("Cron operation failed", e);
                return ToolResult.failure("Cron 操作失敗: " + e.getMessage());
            }
        });
    }

    private ToolResult explain(String expression) {
        String[] parts = expression.trim().split("\\s+");

        if (parts.length < 5 || parts.length > 6) {
            return ToolResult.failure("無效的 Cron 表達式，應為 5 或 6 個欄位");
        }

        boolean hasSeconds = parts.length == 6;
        int offset = hasSeconds ? 1 : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("Cron 表達式解析：\n");
        sb.append(String.format("表達式：%s\n\n", expression));

        if (hasSeconds) {
            sb.append(String.format("秒：%s - %s\n", parts[0], explainField(parts[0], "second")));
        }
        sb.append(String.format("分：%s - %s\n", parts[offset], explainField(parts[offset], "minute")));
        sb.append(String.format("時：%s - %s\n", parts[offset + 1], explainField(parts[offset + 1], "hour")));
        sb.append(String.format("日：%s - %s\n", parts[offset + 2], explainField(parts[offset + 2], "day")));
        sb.append(String.format("月：%s - %s\n", parts[offset + 3], explainField(parts[offset + 3], "month")));
        sb.append(String.format("週：%s - %s\n", parts[offset + 4], explainField(parts[offset + 4], "weekday")));

        sb.append("\n");
        sb.append("總結：").append(generateSummary(parts, hasSeconds));

        return ToolResult.success(sb.toString(), Map.of(
                "expression", expression,
                "fields", parts.length,
                "hasSeconds", hasSeconds
        ));
    }

    private String explainField(String field, String type) {
        if (field.equals("*")) {
            return "每" + getTypeName(type);
        }

        if (field.contains("/")) {
            String[] parts = field.split("/");
            return String.format("從 %s 開始，每隔 %s %s",
                    parts[0].equals("*") ? "0" : parts[0], parts[1], getTypeName(type));
        }

        if (field.contains("-")) {
            String[] parts = field.split("-");
            return String.format("%s 到 %s", parts[0], parts[1]);
        }

        if (field.contains(",")) {
            return "在 " + field.replace(",", "、");
        }

        return "在 " + field;
    }

    private String getTypeName(String type) {
        return switch (type) {
            case "second" -> "秒";
            case "minute" -> "分鐘";
            case "hour" -> "小時";
            case "day" -> "天";
            case "month" -> "月";
            case "weekday" -> "週";
            default -> type;
        };
    }

    private String generateSummary(String[] parts, boolean hasSeconds) {
        int offset = hasSeconds ? 1 : 0;

        String minute = parts[offset];
        String hour = parts[offset + 1];
        String day = parts[offset + 2];
        String month = parts[offset + 3];
        String weekday = parts[offset + 4];

        StringBuilder summary = new StringBuilder();

        // Weekday patterns
        if (!weekday.equals("*")) {
            if (weekday.equals("1-5") || weekday.equals("MON-FRI")) {
                summary.append("週一至週五");
            } else if (weekday.equals("0,6") || weekday.equals("SAT,SUN")) {
                summary.append("週末");
            } else {
                summary.append("每週 ").append(weekday);
            }
        }

        // Time patterns
        if (hour.equals("*") && minute.contains("/")) {
            summary.append("每 ").append(minute.split("/")[1]).append(" 分鐘執行");
        } else if (minute.contains("/") && hour.equals("*")) {
            summary.append("每 ").append(minute.split("/")[1]).append(" 分鐘執行");
        } else if (!hour.equals("*") && !minute.equals("*")) {
            summary.append("在 ").append(hour).append(":").append(minute.length() == 1 ? "0" + minute : minute).append(" 執行");
        } else if (hour.equals("*") && minute.equals("0")) {
            summary.append("每小時整點執行");
        }

        // Day patterns
        if (!day.equals("*") && month.equals("*") && weekday.equals("*")) {
            summary.append("每月 ").append(day).append(" 日");
        }

        if (summary.isEmpty()) {
            summary.append("定期執行");
        }

        return summary.toString();
    }

    private ToolResult nextExecutions(String expression, int count, String timezone) {
        String[] parts = expression.trim().split("\\s+");
        if (parts.length < 5 || parts.length > 6) {
            return ToolResult.failure("無效的 Cron 表達式");
        }

        ZoneId zoneId;
        try {
            zoneId = ZoneId.of(timezone);
        } catch (Exception e) {
            zoneId = ZoneId.systemDefault();
        }

        // Simple next execution calculation
        // Note: This is a simplified implementation; a production system would use a library like cron-utils
        List<String> executions = new ArrayList<>();
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime next = now.plusMinutes(1).withSecond(0).withNano(0);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

        int found = 0;
        int iterations = 0;
        int maxIterations = 525600; // Max 1 year of minutes

        boolean hasSeconds = parts.length == 6;
        int offset = hasSeconds ? 1 : 0;

        while (found < count && iterations < maxIterations) {
            if (matchesCron(next, parts, hasSeconds, offset)) {
                executions.add(formatter.format(next));
                found++;
            }
            next = next.plusMinutes(1);
            iterations++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Cron 表達式：%s\n", expression));
        sb.append(String.format("時區：%s\n\n", zoneId));
        sb.append(String.format("接下來 %d 次執行時間：\n", executions.size()));

        for (int i = 0; i < executions.size(); i++) {
            sb.append(String.format("%d. %s\n", i + 1, executions.get(i)));
        }

        return ToolResult.success(sb.toString(), Map.of(
                "expression", expression,
                "timezone", zoneId.toString(),
                "executions", executions,
                "count", executions.size()
        ));
    }

    private boolean matchesCron(ZonedDateTime time, String[] parts, boolean hasSeconds, int offset) {
        int minute = time.getMinute();
        int hour = time.getHour();
        int dayOfMonth = time.getDayOfMonth();
        int month = time.getMonthValue();
        int dayOfWeek = time.getDayOfWeek().getValue() % 7; // 0 = Sunday

        return matchesField(parts[offset], minute, 0, 59) &&
                matchesField(parts[offset + 1], hour, 0, 23) &&
                matchesField(parts[offset + 2], dayOfMonth, 1, 31) &&
                matchesField(parts[offset + 3], month, 1, 12) &&
                matchesField(parts[offset + 4], dayOfWeek, 0, 6);
    }

    private boolean matchesField(String field, int value, int min, int max) {
        if (field.equals("*")) return true;

        for (String part : field.split(",")) {
            if (part.contains("/")) {
                String[] stepParts = part.split("/");
                int start = stepParts[0].equals("*") ? min : Integer.parseInt(stepParts[0]);
                int step = Integer.parseInt(stepParts[1]);
                for (int i = start; i <= max; i += step) {
                    if (i == value) return true;
                }
            } else if (part.contains("-")) {
                String[] rangeParts = part.split("-");
                int rangeStart = Integer.parseInt(rangeParts[0]);
                int rangeEnd = Integer.parseInt(rangeParts[1]);
                if (value >= rangeStart && value <= rangeEnd) return true;
            } else {
                if (Integer.parseInt(part) == value) return true;
            }
        }
        return false;
    }

    private ToolResult validate(String expression) {
        String[] parts = expression.trim().split("\\s+");

        if (parts.length < 5 || parts.length > 6) {
            return ToolResult.success(
                    "驗證失敗：Cron 表達式應為 5 或 6 個欄位",
                    Map.of("valid", false, "error", "欄位數量錯誤")
            );
        }

        boolean hasSeconds = parts.length == 6;
        int offset = hasSeconds ? 1 : 0;

        try {
            if (hasSeconds) validateField(parts[0], 0, 59, "秒");
            validateField(parts[offset], 0, 59, "分");
            validateField(parts[offset + 1], 0, 23, "時");
            validateField(parts[offset + 2], 1, 31, "日");
            validateField(parts[offset + 3], 1, 12, "月");
            validateField(parts[offset + 4], 0, 6, "週");

            return ToolResult.success(
                    "驗證通過：Cron 表達式格式正確",
                    Map.of("valid", true, "fields", parts.length, "hasSeconds", hasSeconds)
            );
        } catch (IllegalArgumentException e) {
            return ToolResult.success(
                    "驗證失敗：" + e.getMessage(),
                    Map.of("valid", false, "error", e.getMessage())
            );
        }
    }

    private void validateField(String field, int min, int max, String name) {
        for (String part : field.split(",")) {
            if (part.equals("*")) continue;

            if (part.contains("/")) {
                String[] stepParts = part.split("/");
                if (!stepParts[0].equals("*")) {
                    validateNumber(stepParts[0], min, max, name);
                }
                validateNumber(stepParts[1], 1, max, name + " 步長");
            } else if (part.contains("-")) {
                String[] rangeParts = part.split("-");
                validateNumber(rangeParts[0], min, max, name);
                validateNumber(rangeParts[1], min, max, name);
            } else {
                validateNumber(part, min, max, name);
            }
        }
    }

    private void validateNumber(String value, int min, int max, String name) {
        try {
            int num = Integer.parseInt(value);
            if (num < min || num > max) {
                throw new IllegalArgumentException(
                        String.format("%s 值 %d 超出範圍 [%d-%d]", name, num, min, max));
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("%s 值 '%s' 不是有效數字", name, value));
        }
    }

    @Override
    public String getCategory() {
        return "utility";
    }
}
