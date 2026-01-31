package com.aiinpocket.n3n.execution.handler.handlers.data;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.MultiOperationNodeHandler;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * DateTime manipulation node handler.
 *
 * Supports:
 * - Now: Get current date/time
 * - Format: Format date to string
 * - Parse: Parse string to date
 * - Add/Subtract: Date arithmetic
 * - Compare: Compare dates
 * - Extract: Extract date parts
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DateTimeNodeHandler extends MultiOperationNodeHandler {

    @Override
    public String getType() {
        return "datetime";
    }

    @Override
    public String getDisplayName() {
        return "Date & Time";
    }

    @Override
    public String getDescription() {
        return "Work with dates and times: format, parse, calculate, and compare.";
    }

    @Override
    public String getCategory() {
        return "Data";
    }

    @Override
    public String getIcon() {
        return "calendar";
    }

    @Override
    public String getCredentialType() {
        return null;
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("current", ResourceDef.of("current", "Current", "Get current date/time"));
        resources.put("format", ResourceDef.of("format", "Format & Parse", "Format and parse dates"));
        resources.put("calculate", ResourceDef.of("calculate", "Calculate", "Date arithmetic and comparison"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // Current date/time operations
        operations.put("current", List.of(
            OperationDef.create("now", "Now")
                .description("Get current date and time")
                .fields(List.of(
                    FieldDef.string("timezone", "Timezone")
                        .withDefault("UTC")
                        .withDescription("Timezone (e.g., UTC, Asia/Tokyo, America/New_York)")
                        .withPlaceholder("UTC"),
                    FieldDef.string("format", "Format")
                        .withDefault("ISO")
                        .withDescription("Output format (ISO, date, time, or custom pattern)")
                        .withPlaceholder("yyyy-MM-dd HH:mm:ss")
                ))
                .requiresCredential(false)
                .outputDescription("Returns current date/time in various formats")
                .build(),

            OperationDef.create("timestamp", "Unix Timestamp")
                .description("Get current Unix timestamp")
                .fields(List.of(
                    FieldDef.select("unit", "Unit", List.of("seconds", "milliseconds", "nanoseconds"))
                        .withDefault("seconds")
                        .withDescription("Timestamp unit")
                ))
                .requiresCredential(false)
                .outputDescription("Returns Unix timestamp")
                .build()
        ));

        // Format/Parse operations
        operations.put("format", List.of(
            OperationDef.create("format", "Format Date")
                .description("Format a date to a string")
                .fields(List.of(
                    FieldDef.string("date", "Date")
                        .withDescription("Date in ISO format or timestamp")
                        .withPlaceholder("2024-01-15T10:30:00Z")
                        .required(),
                    FieldDef.string("format", "Format Pattern")
                        .withDefault("yyyy-MM-dd HH:mm:ss")
                        .withDescription("Output format pattern")
                        .required(),
                    FieldDef.string("timezone", "Timezone")
                        .withDefault("UTC")
                        .withDescription("Target timezone")
                ))
                .requiresCredential(false)
                .outputDescription("Returns formatted date string")
                .build(),

            OperationDef.create("parse", "Parse Date")
                .description("Parse a string to a date")
                .fields(List.of(
                    FieldDef.string("dateString", "Date String")
                        .withDescription("Date string to parse")
                        .withPlaceholder("2024-01-15 10:30:00")
                        .required(),
                    FieldDef.string("format", "Format Pattern")
                        .withDescription("Input format pattern (leave empty for auto-detect)")
                        .withPlaceholder("yyyy-MM-dd HH:mm:ss"),
                    FieldDef.string("timezone", "Timezone")
                        .withDefault("UTC")
                        .withDescription("Source timezone")
                ))
                .requiresCredential(false)
                .outputDescription("Returns parsed date in ISO format")
                .build(),

            OperationDef.create("convert", "Convert Timezone")
                .description("Convert date to a different timezone")
                .fields(List.of(
                    FieldDef.string("date", "Date")
                        .withDescription("Date in ISO format")
                        .withPlaceholder("2024-01-15T10:30:00Z")
                        .required(),
                    FieldDef.string("fromTimezone", "From Timezone")
                        .withDefault("UTC")
                        .withDescription("Source timezone"),
                    FieldDef.string("toTimezone", "To Timezone")
                        .withDescription("Target timezone")
                        .withPlaceholder("Asia/Tokyo")
                        .required()
                ))
                .requiresCredential(false)
                .outputDescription("Returns date in target timezone")
                .build()
        ));

        // Calculate operations
        operations.put("calculate", List.of(
            OperationDef.create("add", "Add Time")
                .description("Add time to a date")
                .fields(List.of(
                    FieldDef.string("date", "Date")
                        .withDescription("Base date in ISO format")
                        .withPlaceholder("2024-01-15T10:30:00Z")
                        .required(),
                    FieldDef.integer("amount", "Amount")
                        .withDefault(1)
                        .withDescription("Amount to add (use negative to subtract)")
                        .required(),
                    FieldDef.select("unit", "Unit", List.of(
                            "seconds", "minutes", "hours", "days", "weeks", "months", "years"
                        ))
                        .withDefault("days")
                        .withDescription("Time unit")
                        .required()
                ))
                .requiresCredential(false)
                .outputDescription("Returns new date")
                .build(),

            OperationDef.create("diff", "Difference")
                .description("Calculate difference between two dates")
                .fields(List.of(
                    FieldDef.string("date1", "Date 1")
                        .withDescription("First date in ISO format")
                        .required(),
                    FieldDef.string("date2", "Date 2")
                        .withDescription("Second date in ISO format")
                        .required(),
                    FieldDef.select("unit", "Unit", List.of(
                            "seconds", "minutes", "hours", "days", "weeks", "months", "years"
                        ))
                        .withDefault("days")
                        .withDescription("Result unit")
                        .required()
                ))
                .requiresCredential(false)
                .outputDescription("Returns difference in specified unit")
                .build(),

            OperationDef.create("compare", "Compare")
                .description("Compare two dates")
                .fields(List.of(
                    FieldDef.string("date1", "Date 1")
                        .withDescription("First date in ISO format")
                        .required(),
                    FieldDef.string("date2", "Date 2")
                        .withDescription("Second date in ISO format")
                        .required()
                ))
                .requiresCredential(false)
                .outputDescription("Returns comparison result (before, after, equal)")
                .build(),

            OperationDef.create("extract", "Extract Parts")
                .description("Extract parts from a date")
                .fields(List.of(
                    FieldDef.string("date", "Date")
                        .withDescription("Date in ISO format")
                        .required(),
                    FieldDef.string("timezone", "Timezone")
                        .withDefault("UTC")
                        .withDescription("Timezone for extraction")
                ))
                .requiresCredential(false)
                .outputDescription("Returns year, month, day, hour, minute, second, dayOfWeek")
                .build(),

            OperationDef.create("startOf", "Start Of")
                .description("Get start of a time period")
                .fields(List.of(
                    FieldDef.string("date", "Date")
                        .withDescription("Date in ISO format")
                        .required(),
                    FieldDef.select("period", "Period", List.of(
                            "day", "week", "month", "year"
                        ))
                        .withDefault("day")
                        .withDescription("Period to get start of")
                        .required(),
                    FieldDef.string("timezone", "Timezone")
                        .withDefault("UTC")
                        .withDescription("Timezone")
                ))
                .requiresCredential(false)
                .outputDescription("Returns start of period")
                .build(),

            OperationDef.create("endOf", "End Of")
                .description("Get end of a time period")
                .fields(List.of(
                    FieldDef.string("date", "Date")
                        .withDescription("Date in ISO format")
                        .required(),
                    FieldDef.select("period", "Period", List.of(
                            "day", "week", "month", "year"
                        ))
                        .withDefault("day")
                        .withDescription("Period to get end of")
                        .required(),
                    FieldDef.string("timezone", "Timezone")
                        .withDefault("UTC")
                        .withDescription("Timezone")
                ))
                .requiresCredential(false)
                .outputDescription("Returns end of period")
                .build()
        ));

        return operations;
    }

    @Override
    public NodeExecutionResult executeOperation(
        NodeExecutionContext context,
        String resource,
        String operation,
        Map<String, Object> credential,
        Map<String, Object> params
    ) {
        try {
            return switch (resource) {
                case "current" -> switch (operation) {
                    case "now" -> now(params);
                    case "timestamp" -> timestamp(params);
                    default -> NodeExecutionResult.failure("Unknown current operation: " + operation);
                };
                case "format" -> switch (operation) {
                    case "format" -> format(params);
                    case "parse" -> parse(params);
                    case "convert" -> convert(params);
                    default -> NodeExecutionResult.failure("Unknown format operation: " + operation);
                };
                case "calculate" -> switch (operation) {
                    case "add" -> add(params);
                    case "diff" -> diff(params);
                    case "compare" -> compare(params);
                    case "extract" -> extract(params);
                    case "startOf" -> startOf(params);
                    case "endOf" -> endOf(params);
                    default -> NodeExecutionResult.failure("Unknown calculate operation: " + operation);
                };
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (Exception e) {
            log.error("DateTime operation error: {}", e.getMessage());
            return NodeExecutionResult.failure("DateTime error: " + e.getMessage());
        }
    }

    private NodeExecutionResult now(Map<String, Object> params) {
        String timezone = getParam(params, "timezone", "UTC");
        String format = getParam(params, "format", "ISO");

        ZoneId zoneId = ZoneId.of(timezone);
        ZonedDateTime now = ZonedDateTime.now(zoneId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("iso", now.toInstant().toString());
        result.put("date", now.toLocalDate().toString());
        result.put("time", now.toLocalTime().toString());
        result.put("timestamp", now.toEpochSecond());
        result.put("timezone", timezone);

        if (!"ISO".equalsIgnoreCase(format) && !"date".equalsIgnoreCase(format) && !"time".equalsIgnoreCase(format)) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            result.put("formatted", now.format(formatter));
        }

        return NodeExecutionResult.success(result);
    }

    private NodeExecutionResult timestamp(Map<String, Object> params) {
        String unit = getParam(params, "unit", "seconds");
        Instant now = Instant.now();

        long value = switch (unit) {
            case "milliseconds" -> now.toEpochMilli();
            case "nanoseconds" -> now.getEpochSecond() * 1_000_000_000L + now.getNano();
            default -> now.getEpochSecond();
        };

        return NodeExecutionResult.success(Map.of(
            "timestamp", value,
            "unit", unit
        ));
    }

    private NodeExecutionResult format(Map<String, Object> params) {
        String dateStr = getRequiredParam(params, "date");
        String format = getRequiredParam(params, "format");
        String timezone = getParam(params, "timezone", "UTC");

        ZonedDateTime date = parseDate(dateStr, timezone);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format).withZone(ZoneId.of(timezone));
        String formatted = date.format(formatter);

        return NodeExecutionResult.success(Map.of("formatted", formatted));
    }

    private NodeExecutionResult parse(Map<String, Object> params) {
        String dateString = getRequiredParam(params, "dateString");
        String format = getParam(params, "format", "");
        String timezone = getParam(params, "timezone", "UTC");

        ZonedDateTime parsed;
        if (format.isEmpty()) {
            // Try common formats
            parsed = tryParseCommonFormats(dateString, timezone);
        } else {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format).withZone(ZoneId.of(timezone));
            parsed = ZonedDateTime.parse(dateString, formatter);
        }

        return NodeExecutionResult.success(Map.of(
            "iso", parsed.toInstant().toString(),
            "date", parsed.toLocalDate().toString(),
            "time", parsed.toLocalTime().toString(),
            "timestamp", parsed.toEpochSecond()
        ));
    }

    private ZonedDateTime tryParseCommonFormats(String dateString, String timezone) {
        ZoneId zoneId = ZoneId.of(timezone);
        List<DateTimeFormatter> formatters = List.of(
            DateTimeFormatter.ISO_INSTANT,
            DateTimeFormatter.ISO_DATE_TIME,
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss")
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                if (formatter == DateTimeFormatter.ISO_INSTANT) {
                    return Instant.parse(dateString).atZone(zoneId);
                }
                return ZonedDateTime.parse(dateString, formatter.withZone(zoneId));
            } catch (DateTimeParseException e) {
                // Try next format
            }
            try {
                LocalDateTime ldt = LocalDateTime.parse(dateString, formatter);
                return ldt.atZone(zoneId);
            } catch (DateTimeParseException e) {
                // Try next format
            }
            try {
                LocalDate ld = LocalDate.parse(dateString, formatter);
                return ld.atStartOfDay(zoneId);
            } catch (DateTimeParseException e) {
                // Try next format
            }
        }
        throw new IllegalArgumentException("Unable to parse date: " + dateString);
    }

    private NodeExecutionResult convert(Map<String, Object> params) {
        String dateStr = getRequiredParam(params, "date");
        String fromTimezone = getParam(params, "fromTimezone", "UTC");
        String toTimezone = getRequiredParam(params, "toTimezone");

        ZonedDateTime date = parseDate(dateStr, fromTimezone);
        ZonedDateTime converted = date.withZoneSameInstant(ZoneId.of(toTimezone));

        return NodeExecutionResult.success(Map.of(
            "iso", converted.toInstant().toString(),
            "local", converted.toLocalDateTime().toString(),
            "timezone", toTimezone
        ));
    }

    private NodeExecutionResult add(Map<String, Object> params) {
        String dateStr = getRequiredParam(params, "date");
        int amount = getIntParam(params, "amount", 0);
        String unit = getRequiredParam(params, "unit");

        ZonedDateTime date = parseDate(dateStr, "UTC");
        ZonedDateTime result = switch (unit) {
            case "seconds" -> date.plusSeconds(amount);
            case "minutes" -> date.plusMinutes(amount);
            case "hours" -> date.plusHours(amount);
            case "days" -> date.plusDays(amount);
            case "weeks" -> date.plusWeeks(amount);
            case "months" -> date.plusMonths(amount);
            case "years" -> date.plusYears(amount);
            default -> throw new IllegalArgumentException("Unknown unit: " + unit);
        };

        return NodeExecutionResult.success(Map.of(
            "iso", result.toInstant().toString(),
            "date", result.toLocalDate().toString(),
            "time", result.toLocalTime().toString()
        ));
    }

    private NodeExecutionResult diff(Map<String, Object> params) {
        String date1Str = getRequiredParam(params, "date1");
        String date2Str = getRequiredParam(params, "date2");
        String unit = getRequiredParam(params, "unit");

        ZonedDateTime date1 = parseDate(date1Str, "UTC");
        ZonedDateTime date2 = parseDate(date2Str, "UTC");

        long difference = switch (unit) {
            case "seconds" -> ChronoUnit.SECONDS.between(date1, date2);
            case "minutes" -> ChronoUnit.MINUTES.between(date1, date2);
            case "hours" -> ChronoUnit.HOURS.between(date1, date2);
            case "days" -> ChronoUnit.DAYS.between(date1, date2);
            case "weeks" -> ChronoUnit.WEEKS.between(date1, date2);
            case "months" -> ChronoUnit.MONTHS.between(date1, date2);
            case "years" -> ChronoUnit.YEARS.between(date1, date2);
            default -> throw new IllegalArgumentException("Unknown unit: " + unit);
        };

        return NodeExecutionResult.success(Map.of(
            "difference", difference,
            "unit", unit,
            "absolute", Math.abs(difference)
        ));
    }

    private NodeExecutionResult compare(Map<String, Object> params) {
        String date1Str = getRequiredParam(params, "date1");
        String date2Str = getRequiredParam(params, "date2");

        ZonedDateTime date1 = parseDate(date1Str, "UTC");
        ZonedDateTime date2 = parseDate(date2Str, "UTC");

        int comparison = date1.compareTo(date2);
        String result = comparison < 0 ? "before" : (comparison > 0 ? "after" : "equal");

        return NodeExecutionResult.success(Map.of(
            "result", result,
            "isBefore", comparison < 0,
            "isAfter", comparison > 0,
            "isEqual", comparison == 0
        ));
    }

    private NodeExecutionResult extract(Map<String, Object> params) {
        String dateStr = getRequiredParam(params, "date");
        String timezone = getParam(params, "timezone", "UTC");

        ZonedDateTime date = parseDate(dateStr, timezone);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("year", date.getYear());
        result.put("month", date.getMonthValue());
        result.put("day", date.getDayOfMonth());
        result.put("hour", date.getHour());
        result.put("minute", date.getMinute());
        result.put("second", date.getSecond());
        result.put("dayOfWeek", date.getDayOfWeek().getValue());
        result.put("dayOfWeekName", date.getDayOfWeek().toString());
        result.put("dayOfYear", date.getDayOfYear());
        result.put("weekOfYear", date.get(java.time.temporal.WeekFields.ISO.weekOfYear()));
        result.put("quarter", (date.getMonthValue() - 1) / 3 + 1);

        return NodeExecutionResult.success(result);
    }

    private NodeExecutionResult startOf(Map<String, Object> params) {
        String dateStr = getRequiredParam(params, "date");
        String period = getRequiredParam(params, "period");
        String timezone = getParam(params, "timezone", "UTC");

        ZonedDateTime date = parseDate(dateStr, timezone);
        ZonedDateTime result = switch (period) {
            case "day" -> date.toLocalDate().atStartOfDay(date.getZone());
            case "week" -> date.with(java.time.DayOfWeek.MONDAY).toLocalDate().atStartOfDay(date.getZone());
            case "month" -> date.withDayOfMonth(1).toLocalDate().atStartOfDay(date.getZone());
            case "year" -> date.withDayOfYear(1).toLocalDate().atStartOfDay(date.getZone());
            default -> throw new IllegalArgumentException("Unknown period: " + period);
        };

        return NodeExecutionResult.success(Map.of(
            "iso", result.toInstant().toString(),
            "date", result.toLocalDate().toString(),
            "time", result.toLocalTime().toString()
        ));
    }

    private NodeExecutionResult endOf(Map<String, Object> params) {
        String dateStr = getRequiredParam(params, "date");
        String period = getRequiredParam(params, "period");
        String timezone = getParam(params, "timezone", "UTC");

        ZonedDateTime date = parseDate(dateStr, timezone);
        ZonedDateTime result = switch (period) {
            case "day" -> date.toLocalDate().atTime(23, 59, 59, 999999999).atZone(date.getZone());
            case "week" -> date.with(java.time.DayOfWeek.SUNDAY).toLocalDate().atTime(23, 59, 59, 999999999).atZone(date.getZone());
            case "month" -> date.withDayOfMonth(date.getMonth().length(date.toLocalDate().isLeapYear())).toLocalDate().atTime(23, 59, 59, 999999999).atZone(date.getZone());
            case "year" -> date.withDayOfYear(date.toLocalDate().isLeapYear() ? 366 : 365).toLocalDate().atTime(23, 59, 59, 999999999).atZone(date.getZone());
            default -> throw new IllegalArgumentException("Unknown period: " + period);
        };

        return NodeExecutionResult.success(Map.of(
            "iso", result.toInstant().toString(),
            "date", result.toLocalDate().toString(),
            "time", result.toLocalTime().toString()
        ));
    }

    private ZonedDateTime parseDate(String dateStr, String timezone) {
        try {
            // Try ISO instant first
            return Instant.parse(dateStr).atZone(ZoneId.of(timezone));
        } catch (DateTimeParseException e) {
            // Try other formats
            return tryParseCommonFormats(dateStr, timezone);
        }
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
