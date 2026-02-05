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
 * 日期時間工具
 *
 * 允許 AI Agent 進行日期時間操作：
 * - 取得當前時間
 * - 格式化日期時間
 * - 計算日期差異
 * - 日期加減運算
 * - 時區轉換
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
            "long", "yyyy年MM月dd日 HH時mm分ss秒"
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
                日期時間操作工具。支援的操作：
                - now: 取得當前日期時間
                - format: 格式化日期時間
                - parse: 解析日期時間字串
                - diff: 計算兩個日期的差異
                - add: 日期加減運算
                - convert: 時區轉換

                預設格式：iso, date, time, datetime, rfc, short, long
                自訂格式使用 Java DateTimeFormatter 語法
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
                                "description", "操作類型",
                                "default", "now"
                        ),
                        "datetime", Map.of(
                                "type", "string",
                                "description", "日期時間字串（ISO 8601 格式）"
                        ),
                        "datetime2", Map.of(
                                "type", "string",
                                "description", "第二個日期時間（用於 diff 操作）"
                        ),
                        "format", Map.of(
                                "type", "string",
                                "description", "日期時間格式（預設 iso）",
                                "default", "iso"
                        ),
                        "timezone", Map.of(
                                "type", "string",
                                "description", "時區（如 Asia/Taipei, UTC, America/New_York）",
                                "default", "Asia/Taipei"
                        ),
                        "target_timezone", Map.of(
                                "type", "string",
                                "description", "目標時區（用於 convert 操作）"
                        ),
                        "amount", Map.of(
                                "type", "integer",
                                "description", "加減的數量（用於 add 操作）"
                        ),
                        "unit", Map.of(
                                "type", "string",
                                "enum", List.of("years", "months", "weeks", "days", "hours", "minutes", "seconds"),
                                "description", "時間單位（用於 add、diff 操作）",
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
                    return ToolResult.failure("無效的時區: " + timezone);
                }

                return switch (operation.toLowerCase()) {
                    case "now" -> handleNow(parameters, zoneId);
                    case "format" -> handleFormat(parameters, zoneId);
                    case "parse" -> handleParse(parameters, zoneId);
                    case "diff" -> handleDiff(parameters, zoneId);
                    case "add" -> handleAdd(parameters, zoneId);
                    case "convert" -> handleConvert(parameters, zoneId);
                    default -> ToolResult.failure("不支援的操作: " + operation);
                };

            } catch (Exception e) {
                log.error("DateTime operation failed", e);
                return ToolResult.failure("日期時間操作失敗: " + e.getMessage());
            }
        });
    }

    /**
     * 取得當前時間
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
                String.format("現在時間（%s）: %s", zoneId, formatted),
                data
        );
    }

    /**
     * 格式化日期時間
     */
    private ToolResult handleFormat(Map<String, Object> parameters, ZoneId zoneId) {
        String datetimeStr = (String) parameters.get("datetime");
        if (datetimeStr == null || datetimeStr.isBlank()) {
            return ToolResult.failure("format 操作需要提供 datetime 參數");
        }

        String formatName = (String) parameters.getOrDefault("format", "iso");
        DateTimeFormatter formatter = getFormatter(formatName);

        ZonedDateTime datetime = parseDateTime(datetimeStr, zoneId);
        if (datetime == null) {
            return ToolResult.failure("無法解析日期時間: " + datetimeStr);
        }

        String formatted = datetime.format(formatter);

        return ToolResult.success(
                String.format("格式化結果: %s", formatted),
                Map.of(
                        "input", datetimeStr,
                        "format", formatName,
                        "formatted", formatted
                )
        );
    }

    /**
     * 解析日期時間
     */
    private ToolResult handleParse(Map<String, Object> parameters, ZoneId zoneId) {
        String datetimeStr = (String) parameters.get("datetime");
        if (datetimeStr == null || datetimeStr.isBlank()) {
            return ToolResult.failure("parse 操作需要提供 datetime 參數");
        }

        ZonedDateTime datetime = parseDateTime(datetimeStr, zoneId);
        if (datetime == null) {
            return ToolResult.failure("無法解析日期時間: " + datetimeStr);
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
                String.format("解析結果: %s", datetime),
                data
        );
    }

    /**
     * 計算日期差異
     */
    private ToolResult handleDiff(Map<String, Object> parameters, ZoneId zoneId) {
        String datetime1Str = (String) parameters.get("datetime");
        String datetime2Str = (String) parameters.get("datetime2");

        if (datetime1Str == null || datetime2Str == null) {
            return ToolResult.failure("diff 操作需要提供 datetime 和 datetime2 參數");
        }

        ZonedDateTime datetime1 = parseDateTime(datetime1Str, zoneId);
        ZonedDateTime datetime2 = parseDateTime(datetime2Str, zoneId);

        if (datetime1 == null || datetime2 == null) {
            return ToolResult.failure("無法解析日期時間");
        }

        String unit = (String) parameters.getOrDefault("unit", "days");
        ChronoUnit chronoUnit = getChronoUnit(unit);

        long diff = chronoUnit.between(datetime1, datetime2);

        // 計算詳細差異
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
        data.put("human_readable", String.format("%d 天 %d 小時 %d 分 %d 秒", days, hours, minutes, seconds));

        return ToolResult.success(
                String.format("日期差異: %d %s (%d 天 %d 小時 %d 分 %d 秒)",
                        diff, unit, days, hours, minutes, seconds),
                data
        );
    }

    /**
     * 日期加減
     */
    private ToolResult handleAdd(Map<String, Object> parameters, ZoneId zoneId) {
        String datetimeStr = (String) parameters.get("datetime");
        Integer amount = parameters.containsKey("amount")
                ? ((Number) parameters.get("amount")).intValue()
                : null;

        if (amount == null) {
            return ToolResult.failure("add 操作需要提供 amount 參數");
        }

        ZonedDateTime datetime;
        if (datetimeStr == null || datetimeStr.isBlank()) {
            datetime = ZonedDateTime.now(zoneId);
        } else {
            datetime = parseDateTime(datetimeStr, zoneId);
            if (datetime == null) {
                return ToolResult.failure("無法解析日期時間: " + datetimeStr);
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

        String operation = amount >= 0 ? "加" : "減";
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
     * 時區轉換
     */
    private ToolResult handleConvert(Map<String, Object> parameters, ZoneId sourceZone) {
        String datetimeStr = (String) parameters.get("datetime");
        String targetTz = (String) parameters.get("target_timezone");

        if (targetTz == null || targetTz.isBlank()) {
            return ToolResult.failure("convert 操作需要提供 target_timezone 參數");
        }

        ZoneId targetZone;
        try {
            targetZone = ZoneId.of(targetTz);
        } catch (Exception e) {
            return ToolResult.failure("無效的目標時區: " + targetTz);
        }

        ZonedDateTime datetime;
        if (datetimeStr == null || datetimeStr.isBlank()) {
            datetime = ZonedDateTime.now(sourceZone);
        } else {
            datetime = parseDateTime(datetimeStr, sourceZone);
            if (datetime == null) {
                return ToolResult.failure("無法解析日期時間: " + datetimeStr);
            }
        }

        ZonedDateTime converted = datetime.withZoneSameInstant(targetZone);

        return ToolResult.success(
                String.format("時區轉換: %s (%s) -> %s (%s)",
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
     * 取得格式化器
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
     * 解析日期時間字串
     */
    private ZonedDateTime parseDateTime(String datetimeStr, ZoneId defaultZone) {
        try {
            // 嘗試解析 ZonedDateTime
            return ZonedDateTime.parse(datetimeStr);
        } catch (DateTimeParseException e1) {
            try {
                // 嘗試解析 LocalDateTime
                LocalDateTime ldt = LocalDateTime.parse(datetimeStr);
                return ldt.atZone(defaultZone);
            } catch (DateTimeParseException e2) {
                try {
                    // 嘗試解析 LocalDate
                    LocalDate ld = LocalDate.parse(datetimeStr);
                    return ld.atStartOfDay(defaultZone);
                } catch (DateTimeParseException e3) {
                    try {
                        // 嘗試常見格式
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
     * 取得 ChronoUnit
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
