package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Date/time tool
 *
 * Allows AI Agent to perform date/time operations:
 * - Get current time
 * - Format date/time
 * - Calculate date differences
 * - Date arithmetic (add/subtract)
 * - Timezone conversion
 */
@Component
@Slf4j
public class DateTimeTool implements AgentNodeTool {

    private static final Map<String, String> FORMAT_PRESETS = Map.of(
            "iso", "yyyy-MM-dd'T'HH:mm:ss",
            "date", "yyyy-MM-dd",
            "time", "HH:mm:ss",
            "datetime", "yyyy-MM-dd HH:mm:ss",
            "rfc", "EEE, dd MMM yyyy HH:mm:ss z",
            "short", "yy/MM/dd",
            "long", "MMMM dd, yyyy HH:mm:ss"
    );

    @Override
    public String getId() {
        return "datetime";
    }

    @Override
    public String getName() {
        return "Date Time";
    }

    @Override
    public String getDescription() {
        return """
                Date/time operation tool. Supported operations:
                - now: Get current date/time
                - format: Format date/time
                - parse: Parse date/time string
                - diff: Calculate difference between two dates
                - add: Date arithmetic (add/subtract)
                - convert: Timezone conversion

                Preset formats: iso, date, time, datetime, rfc, short, long
                Custom formats use Java DateTimeFormatter syntax
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("now", "format", "parse", "diff", "add", "convert"),
                                "description", "Operation type",
                                "default", "now"
                        ),
                        "datetime", Map.of(
                                "type", "string",
                                "description", "Date/time string (ISO 8601 format)"
                        ),
                        "datetime2", Map.of(
                                "type", "string",
                                "description", "Second date/time (for diff operation)"
                        ),
                        "format", Map.of(
                                "type", "string",
                                "description", "Date/time format (default iso)",
                                "default", "iso"
                        ),
                        "timezone", Map.of(
                                "type", "string",
                                "description", "Timezone (e.g. Asia/Taipei, UTC, America/New_York)",
                                "default", "Asia/Taipei"
                        ),
                        "target_timezone", Map.of(
                                "type", "string",
                                "description", "Target timezone (for convert operation)"
                        ),
                        "amount", Map.of(
                                "type", "integer",
                                "description", "Amount to add/subtract (for add operation)"
                        ),
                        "unit", Map.of(
                                "type", "string",
                                "enum", List.of("years", "months", "weeks", "days", "hours", "minutes", "seconds"),
                                "description", "Time unit (for add, diff operations)",
                                "default", "days"
                        )
                ),
                "required", List.of()
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String operation = (String) parameters.getOrDefault("operation", "now");
                String timezone = (String) parameters.getOrDefault("timezone", "Asia/Taipei");

                ZoneId zoneId;
                try {
                    zoneId = ZoneId.of(timezone);
                } catch (Exception e) {
                    return ToolResult.failure("Invalid timezone: " + timezone);
                }

                return switch (operation.toLowerCase()) {
                    case "now" -> handleNow(parameters, zoneId);
                    case "format" -> handleFormat(parameters, zoneId);
                    case "parse" -> handleParse(parameters, zoneId);
                    case "diff" -> handleDiff(parameters, zoneId);
                    case "add" -> handleAdd(parameters, zoneId);
                    case "convert" -> handleConvert(parameters, zoneId);
                    default -> ToolResult.failure("Unsupported operation: " + operation);
                };

            } catch (Exception e) {
                log.error("DateTime operation failed", e);
                return ToolResult.failure("Date/time operation failed");
            }
        });
    }

    /**
     * Get current time
     */
    private ToolResult handleNow(Map<String, Object> parameters, ZoneId zoneId) {
        String formatName = (String) parameters.getOrDefault("format", "iso");
        DateTimeFormatter formatter = getFormatter(formatName);

        ZonedDateTime now = ZonedDateTime.now(zoneId);
        String formatted = now.format(formatter);

        Map<String, Object> data = new HashMap<>();
        data.put("datetime", now.toString());
        data.put("formatted", formatted);
        data.put("timezone", zoneId.toString());
        data.put("year", now.getYear());
        data.put("month", now.getMonthValue());
        data.put("day", now.getDayOfMonth());
        data.put("hour", now.getHour());
        data.put("minute", now.getMinute());
        data.put("second", now.getSecond());
        data.put("dayOfWeek", now.getDayOfWeek().toString());
        data.put("timestamp", now.toEpochSecond());

        return ToolResult.success(
                String.format("Current time (%s): %s", zoneId, formatted),
                data
        );
    }

    /**
     * Format date/time
     */
    private ToolResult handleFormat(Map<String, Object> parameters, ZoneId zoneId) {
        String datetimeStr = (String) parameters.get("datetime");
        if (datetimeStr == null || datetimeStr.isBlank()) {
            return ToolResult.failure("The format operation requires a datetime parameter");
        }

        String formatName = (String) parameters.getOrDefault("format", "iso");
        DateTimeFormatter formatter = getFormatter(formatName);

        ZonedDateTime datetime = parseDateTime(datetimeStr, zoneId);
        if (datetime == null) {
            return ToolResult.failure("Cannot parse date/time: " + datetimeStr);
        }

        String formatted = datetime.format(formatter);

        return ToolResult.success(
                String.format("Formatted result: %s", formatted),
                Map.of(
                        "input", datetimeStr,
                        "format", formatName,
                        "formatted", formatted
                )
        );
    }

    /**
     * Parse date/time
     */
    private ToolResult handleParse(Map<String, Object> parameters, ZoneId zoneId) {
        String datetimeStr = (String) parameters.get("datetime");
        if (datetimeStr == null || datetimeStr.isBlank()) {
            return ToolResult.failure("The parse operation requires a datetime parameter");
        }

        ZonedDateTime datetime = parseDateTime(datetimeStr, zoneId);
        if (datetime == null) {
            return ToolResult.failure("Cannot parse date/time: " + datetimeStr);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("iso", datetime.toString());
        data.put("timestamp", datetime.toEpochSecond());
        data.put("year", datetime.getYear());
        data.put("month", datetime.getMonthValue());
        data.put("day", datetime.getDayOfMonth());
        data.put("hour", datetime.getHour());
        data.put("minute", datetime.getMinute());
        data.put("second", datetime.getSecond());
        data.put("dayOfWeek", datetime.getDayOfWeek().toString());
        data.put("dayOfYear", datetime.getDayOfYear());

        return ToolResult.success(
                String.format("Parse result: %s", datetime),
                data
        );
    }

    /**
     * Calculate date difference
     */
    private ToolResult handleDiff(Map<String, Object> parameters, ZoneId zoneId) {
        String datetime1Str = (String) parameters.get("datetime");
        String datetime2Str = (String) parameters.get("datetime2");

        if (datetime1Str == null || datetime2Str == null) {
            return ToolResult.failure("The diff operation requires datetime and datetime2 parameters");
        }

        ZonedDateTime datetime1 = parseDateTime(datetime1Str, zoneId);
        ZonedDateTime datetime2 = parseDateTime(datetime2Str, zoneId);

        if (datetime1 == null || datetime2 == null) {
            return ToolResult.failure("Cannot parse date/time");
        }

        String unit = (String) parameters.getOrDefault("unit", "days");
        ChronoUnit chronoUnit = getChronoUnit(unit);

        long diff = chronoUnit.between(datetime1, datetime2);

        // Calculate detailed difference
        Duration duration = Duration.between(datetime1, datetime2);
        long totalSeconds = Math.abs(duration.getSeconds());
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        Map<String, Object> data = new HashMap<>();
        data.put("diff", diff);
        data.put("unit", unit);
        data.put("total_days", days);
        data.put("total_hours", duration.toHours());
        data.put("total_minutes", duration.toMinutes());
        data.put("total_seconds", totalSeconds);
        data.put("human_readable", String.format("%d days %d hours %d minutes %d seconds", days, hours, minutes, seconds));

        return ToolResult.success(
                String.format("Date difference: %d %s (%d days %d hours %d minutes %d seconds)",
                        diff, unit, days, hours, minutes, seconds),
                data
        );
    }

    /**
     * Date arithmetic
     */
    private ToolResult handleAdd(Map<String, Object> parameters, ZoneId zoneId) {
        String datetimeStr = (String) parameters.get("datetime");
        Integer amount = parameters.containsKey("amount")
                ? ((Number) parameters.get("amount")).intValue()
                : null;

        if (amount == null) {
            return ToolResult.failure("The add operation requires an amount parameter");
        }

        ZonedDateTime datetime;
        if (datetimeStr == null || datetimeStr.isBlank()) {
            datetime = ZonedDateTime.now(zoneId);
        } else {
            datetime = parseDateTime(datetimeStr, zoneId);
            if (datetime == null) {
                return ToolResult.failure("Cannot parse date/time: " + datetimeStr);
            }
        }

        String unit = (String) parameters.getOrDefault("unit", "days");
        ZonedDateTime result = switch (unit.toLowerCase()) {
            case "years" -> datetime.plusYears(amount);
            case "months" -> datetime.plusMonths(amount);
            case "weeks" -> datetime.plusWeeks(amount);
            case "days" -> datetime.plusDays(amount);
            case "hours" -> datetime.plusHours(amount);
            case "minutes" -> datetime.plusMinutes(amount);
            case "seconds" -> datetime.plusSeconds(amount);
            default -> datetime.plusDays(amount);
        };

        String operation = amount >= 0 ? "+" : "-";
        int absAmount = Math.abs(amount);

        return ToolResult.success(
                String.format("%s %s %d %s = %s",
                        datetime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        operation, absAmount, unit,
                        result.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                Map.of(
                        "original", datetime.toString(),
                        "result", result.toString(),
                        "amount", amount,
                        "unit", unit
                )
        );
    }

    /**
     * Timezone conversion
     */
    private ToolResult handleConvert(Map<String, Object> parameters, ZoneId sourceZone) {
        String datetimeStr = (String) parameters.get("datetime");
        String targetTz = (String) parameters.get("target_timezone");

        if (targetTz == null || targetTz.isBlank()) {
            return ToolResult.failure("The convert operation requires a target_timezone parameter");
        }

        ZoneId targetZone;
        try {
            targetZone = ZoneId.of(targetTz);
        } catch (Exception e) {
            return ToolResult.failure("Invalid target timezone: " + targetTz);
        }

        ZonedDateTime datetime;
        if (datetimeStr == null || datetimeStr.isBlank()) {
            datetime = ZonedDateTime.now(sourceZone);
        } else {
            datetime = parseDateTime(datetimeStr, sourceZone);
            if (datetime == null) {
                return ToolResult.failure("Cannot parse date/time: " + datetimeStr);
            }
        }

        ZonedDateTime converted = datetime.withZoneSameInstant(targetZone);

        return ToolResult.success(
                String.format("Timezone conversion: %s (%s) -> %s (%s)",
                        datetime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), sourceZone,
                        converted.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), targetZone),
                Map.of(
                        "source", datetime.toString(),
                        "source_timezone", sourceZone.toString(),
                        "target", converted.toString(),
                        "target_timezone", targetZone.toString()
                )
        );
    }

    /**
     * Get formatter
     */
    private DateTimeFormatter getFormatter(String formatName) {
        String pattern = FORMAT_PRESETS.getOrDefault(formatName.toLowerCase(), formatName);
        try {
            return DateTimeFormatter.ofPattern(pattern);
        } catch (Exception e) {
            return DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        }
    }

    /**
     * Parse date/time string
     */
    private ZonedDateTime parseDateTime(String datetimeStr, ZoneId defaultZone) {
        try {
            // Try parsing ZonedDateTime
            return ZonedDateTime.parse(datetimeStr);
        } catch (DateTimeParseException e1) {
            try {
                // Try parsing LocalDateTime
                LocalDateTime ldt = LocalDateTime.parse(datetimeStr);
                return ldt.atZone(defaultZone);
            } catch (DateTimeParseException e2) {
                try {
                    // Try parsing LocalDate
                    LocalDate ld = LocalDate.parse(datetimeStr);
                    return ld.atStartOfDay(defaultZone);
                } catch (DateTimeParseException e3) {
                    try {
                        // Try common format
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        LocalDateTime ldt = LocalDateTime.parse(datetimeStr, formatter);
                        return ldt.atZone(defaultZone);
                    } catch (Exception e4) {
                        return null;
                    }
                }
            }
        }
    }

    /**
     * Get ChronoUnit
     */
    private ChronoUnit getChronoUnit(String unit) {
        return switch (unit.toLowerCase()) {
            case "years" -> ChronoUnit.YEARS;
            case "months" -> ChronoUnit.MONTHS;
            case "weeks" -> ChronoUnit.WEEKS;
            case "days" -> ChronoUnit.DAYS;
            case "hours" -> ChronoUnit.HOURS;
            case "minutes" -> ChronoUnit.MINUTES;
            case "seconds" -> ChronoUnit.SECONDS;
            default -> ChronoUnit.DAYS;
        };
    }

    @Override
    public String getCategory() {
        return "utility";
    }
}
