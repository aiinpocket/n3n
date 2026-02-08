package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Cron expression tool
 * Parse and explain cron expressions
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
                Cron expression tool, supports the following operations:
                - explain: Explain the meaning of a cron expression
                - next: Calculate the next N execution times
                - validate: Validate cron expression format

                Supports standard 5-field format (minute hour day month weekday) and 6-field format (second minute hour day month weekday).

                Parameters:
                - expression: Cron expression
                - operation: Operation type (default explain)
                - count: Number of next executions to calculate (for next, default 5)
                - timezone: Timezone (default system timezone)

                Example expressions:
                - "0 0 * * *" - Every day at midnight
                - "*/15 * * * *" - Every 15 minutes
                - "0 9 * * 1-5" - Monday to Friday at 9 AM
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "expression", Map.of(
                                "type", "string",
                                "description", "Cron expression"
                        ),
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("explain", "next", "validate"),
                                "description", "Operation type",
                                "default", "explain"
                        ),
                        "count", Map.of(
                                "type", "integer",
                                "description", "Number of next executions to calculate",
                                "default", 5
                        ),
                        "timezone", Map.of(
                                "type", "string",
                                "description", "Timezone",
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
                    return ToolResult.failure("Cron expression cannot be empty");
                }

                // Security: limit expression length
                if (expression.length() > 100) {
                    return ToolResult.failure("Expression too long");
                }

                return switch (operation) {
                    case "explain" -> explain(expression);
                    case "next" -> nextExecutions(expression, count, timezone);
                    case "validate" -> validate(expression);
                    default -> ToolResult.failure("Unsupported operation: " + operation);
                };

            } catch (Exception e) {
                log.error("Cron operation failed", e);
                return ToolResult.failure("Cron operation failed");
            }
        });
    }

    private ToolResult explain(String expression) {
        String[] parts = expression.trim().split("\\s+");

        if (parts.length < 5 || parts.length > 6) {
            return ToolResult.failure("Invalid cron expression, should be 5 or 6 fields");
        }

        boolean hasSeconds = parts.length == 6;
        int offset = hasSeconds ? 1 : 0;

        StringBuilder sb = new StringBuilder();
        sb.append("Cron expression analysis:\n");
        sb.append(String.format("Expression: %s\n\n", expression));

        if (hasSeconds) {
            sb.append(String.format("Second: %s - %s\n", parts[0], explainField(parts[0], "second")));
        }
        sb.append(String.format("Minute: %s - %s\n", parts[offset], explainField(parts[offset], "minute")));
        sb.append(String.format("Hour: %s - %s\n", parts[offset + 1], explainField(parts[offset + 1], "hour")));
        sb.append(String.format("Day: %s - %s\n", parts[offset + 2], explainField(parts[offset + 2], "day")));
        sb.append(String.format("Month: %s - %s\n", parts[offset + 3], explainField(parts[offset + 3], "month")));
        sb.append(String.format("Weekday: %s - %s\n", parts[offset + 4], explainField(parts[offset + 4], "weekday")));

        sb.append("\n");
        sb.append("Summary: ").append(generateSummary(parts, hasSeconds));

        return ToolResult.success(sb.toString(), Map.of(
                "expression", expression,
                "fields", parts.length,
                "hasSeconds", hasSeconds
        ));
    }

    private String explainField(String field, String type) {
        if (field.equals("*")) {
            return "every " + getTypeName(type);
        }

        if (field.contains("/")) {
            String[] parts = field.split("/");
            if (parts.length >= 2) {
                return String.format("starting from %s, every %s %s",
                        parts[0].equals("*") ? "0" : parts[0], parts[1], getTypeName(type));
            }
        }

        if (field.contains("-")) {
            String[] parts = field.split("-");
            if (parts.length >= 2) {
                return String.format("%s to %s", parts[0], parts[1]);
            }
        }

        if (field.contains(",")) {
            return "at " + field.replace(",", ", ");
        }

        return "at " + field;
    }

    private String getTypeName(String type) {
        return switch (type) {
            case "second" -> "second";
            case "minute" -> "minute";
            case "hour" -> "hour";
            case "day" -> "day";
            case "month" -> "month";
            case "weekday" -> "weekday";
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
                summary.append("Monday to Friday");
            } else if (weekday.equals("0,6") || weekday.equals("SAT,SUN")) {
                summary.append("Weekends");
            } else {
                summary.append("Weekly on ").append(weekday);
            }
        }

        // Time patterns
        String[] minuteParts = minute.contains("/") ? minute.split("/") : null;
        String minuteInterval = minuteParts != null && minuteParts.length >= 2 ? minuteParts[1] : minute;
        if (hour.equals("*") && minute.contains("/")) {
            summary.append(" every ").append(minuteInterval).append(" minutes");
        } else if (minute.contains("/") && hour.equals("*")) {
            summary.append(" every ").append(minuteInterval).append(" minutes");
        } else if (!hour.equals("*") && !minute.equals("*")) {
            summary.append(" at ").append(hour).append(":").append(minute.length() == 1 ? "0" + minute : minute);
        } else if (hour.equals("*") && minute.equals("0")) {
            summary.append(" every hour on the hour");
        }

        // Day patterns
        if (!day.equals("*") && month.equals("*") && weekday.equals("*")) {
            summary.append(" on day ").append(day).append(" of every month");
        }

        if (summary.isEmpty()) {
            summary.append("Runs periodically");
        }

        return summary.toString();
    }

    private ToolResult nextExecutions(String expression, int count, String timezone) {
        String[] parts = expression.trim().split("\\s+");
        if (parts.length < 5 || parts.length > 6) {
            return ToolResult.failure("Invalid cron expression");
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
        sb.append(String.format("Cron expression: %s\n", expression));
        sb.append(String.format("Timezone: %s\n\n", zoneId));
        sb.append(String.format("Next %d execution times:\n", executions.size()));

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
                    "Validation failed: Cron expression should have 5 or 6 fields",
                    Map.of("valid", false, "error", "Incorrect number of fields")
            );
        }

        boolean hasSeconds = parts.length == 6;
        int offset = hasSeconds ? 1 : 0;

        try {
            if (hasSeconds) validateField(parts[0], 0, 59, "second");
            validateField(parts[offset], 0, 59, "minute");
            validateField(parts[offset + 1], 0, 23, "hour");
            validateField(parts[offset + 2], 1, 31, "day");
            validateField(parts[offset + 3], 1, 12, "month");
            validateField(parts[offset + 4], 0, 6, "weekday");

            return ToolResult.success(
                    "Validation passed: Cron expression format is correct",
                    Map.of("valid", true, "fields", parts.length, "hasSeconds", hasSeconds)
            );
        } catch (IllegalArgumentException e) {
            return ToolResult.success(
                    "Validation failed: " + e.getMessage(),
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
                validateNumber(stepParts[1], 1, max, name + " step");
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
                        String.format("%s value %d is out of range [%d-%d]", name, num, min, max));
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("%s value '%s' is not a valid number", name, value));
        }
    }

    @Override
    public String getCategory() {
        return "utility";
    }
}
