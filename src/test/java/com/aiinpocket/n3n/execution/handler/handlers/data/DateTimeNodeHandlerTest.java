package com.aiinpocket.n3n.execution.handler.handlers.data;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class DateTimeNodeHandlerTest {

    private DateTimeNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DateTimeNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsDatetime() {
        assertThat(handler.getType()).isEqualTo("datetime");
    }

    @Test
    void getCategory_returnsData() {
        assertThat(handler.getCategory()).isEqualTo("Data");
    }

    @Test
    void getDisplayName_returnsDateTime() {
        assertThat(handler.getDisplayName()).isEqualTo("Date & Time");
    }

    @Test
    void getCredentialType_returnsNull() {
        assertThat(handler.getCredentialType()).isNull();
    }

    @Test
    void getResources_containsExpectedResources() {
        var resources = handler.getResources();
        assertThat(resources).containsKey("current");
        assertThat(resources).containsKey("format");
        assertThat(resources).containsKey("calculate");
    }

    @Test
    void getOperations_currentHasExpectedOps() {
        var operations = handler.getOperations();
        var currentOps = operations.get("current");
        assertThat(currentOps).isNotNull();
        var opNames = currentOps.stream().map(op -> op.getName()).toList();
        assertThat(opNames).containsExactly("now", "timestamp");
    }

    // ========== Current: Now ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_now_returnsCurrentDateTimeFields() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "current",
                "operation", "now",
                "timezone", "UTC"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsKey("iso");
        assertThat(result.getOutput()).containsKey("date");
        assertThat(result.getOutput()).containsKey("time");
        assertThat(result.getOutput()).containsKey("timestamp");
        assertThat(result.getOutput()).containsKey("timezone");
        assertThat(result.getOutput().get("iso")).isNotNull();
        assertThat(result.getOutput().get("timestamp")).isInstanceOf(Long.class);
        assertThat(result.getOutput().get("timezone")).isEqualTo("UTC");
    }

    @Test
    void execute_now_withNonUtcTimezone_returnsCorrectTimezone() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "current",
                "operation", "now",
                "timezone", "Asia/Tokyo"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("timezone")).isEqualTo("Asia/Tokyo");
    }

    // ========== Current: Timestamp ==========

    @Test
    void execute_timestampSeconds_returnsTimestampValue() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "current",
                "operation", "timestamp",
                "unit", "seconds"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsKey("timestamp");
        assertThat(result.getOutput()).containsEntry("unit", "seconds");
        long ts = ((Number) result.getOutput().get("timestamp")).longValue();
        // Should be within a reasonable range (after 2024)
        assertThat(ts).isGreaterThan(1700000000L);
    }

    @Test
    void execute_timestampMilliseconds_returnsLargerValue() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "current",
                "operation", "timestamp",
                "unit", "milliseconds"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        long tsMs = ((Number) result.getOutput().get("timestamp")).longValue();
        // Milliseconds should be > seconds * 1000
        assertThat(tsMs).isGreaterThan(1700000000000L);
    }

    // ========== Format: Format ==========

    @Test
    void execute_formatDate_formatsCorrectly() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "format",
                "operation", "format",
                "date", "2024-06-15T10:30:00Z",
                "format", "yyyy-MM-dd",
                "timezone", "UTC"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("formatted", "2024-06-15");
    }

    @Test
    void execute_formatDateWithTimePattern_returnsFormattedDateTime() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "format",
                "operation", "format",
                "date", "2024-06-15T10:30:00Z",
                "format", "yyyy/MM/dd HH:mm",
                "timezone", "UTC"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("formatted", "2024/06/15 10:30");
    }

    // ========== Format: Parse ==========

    @Test
    void execute_parseIsoDateString_returnsDateComponents() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "format",
                "operation", "parse",
                "dateString", "2024-06-15T10:30:00Z"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsKey("iso");
        assertThat(result.getOutput()).containsKey("date");
        assertThat(result.getOutput()).containsKey("time");
        assertThat(result.getOutput()).containsKey("timestamp");
        assertThat(result.getOutput().get("date").toString()).isEqualTo("2024-06-15");
    }

    @Test
    void execute_parseDateWithCommonFormat_autoDetects() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "format",
                "operation", "parse",
                "dateString", "2024-06-15 10:30:00",
                "timezone", "UTC"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("date").toString()).isEqualTo("2024-06-15");
    }

    // ========== Format: Convert Timezone ==========

    @Test
    void execute_convertTimezone_utcToTokyo() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "format",
                "operation", "convert",
                "date", "2024-06-15T10:00:00Z",
                "fromTimezone", "UTC",
                "toTimezone", "Asia/Tokyo"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("timezone", "Asia/Tokyo");
        // Tokyo is UTC+9, so 10:00 UTC = 19:00 Tokyo
        String local = result.getOutput().get("local").toString();
        assertThat(local).contains("19:00");
    }

    // ========== Calculate: Add ==========

    @Test
    void execute_addDays_addsCorrectly() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "calculate",
                "operation", "add",
                "date", "2024-01-15T00:00:00Z",
                "amount", 10,
                "unit", "days"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("date").toString()).isEqualTo("2024-01-25");
    }

    @Test
    void execute_addNegativeDays_subtractsCorrectly() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "calculate",
                "operation", "add",
                "date", "2024-01-15T00:00:00Z",
                "amount", -5,
                "unit", "days"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("date").toString()).isEqualTo("2024-01-10");
    }

    @Test
    void execute_addMonths_acrossBoundary() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "calculate",
                "operation", "add",
                "date", "2024-01-31T00:00:00Z",
                "amount", 1,
                "unit", "months"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        // Jan 31 + 1 month = Feb 29 (2024 is leap year)
        assertThat(result.getOutput().get("date").toString()).isEqualTo("2024-02-29");
    }

    // ========== Calculate: Diff ==========

    @Test
    void execute_diffDays_calculatesCorrectDifference() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "calculate",
                "operation", "diff",
                "date1", "2024-01-01T00:00:00Z",
                "date2", "2024-01-11T00:00:00Z",
                "unit", "days"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("difference", 10L);
        assertThat(result.getOutput()).containsEntry("absolute", 10L);
        assertThat(result.getOutput()).containsEntry("unit", "days");
    }

    @Test
    void execute_diffDays_negativeDifference() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "calculate",
                "operation", "diff",
                "date1", "2024-01-11T00:00:00Z",
                "date2", "2024-01-01T00:00:00Z",
                "unit", "days"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("difference", -10L);
        assertThat(result.getOutput()).containsEntry("absolute", 10L);
    }

    @Test
    void execute_diffHours_calculatesCorrectly() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "calculate",
                "operation", "diff",
                "date1", "2024-01-01T00:00:00Z",
                "date2", "2024-01-01T12:00:00Z",
                "unit", "hours"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("difference", 12L);
    }

    // ========== Calculate: Compare ==========

    @Test
    void execute_compareBefore_returnsBeforeResult() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "calculate",
                "operation", "compare",
                "date1", "2024-01-01T00:00:00Z",
                "date2", "2024-06-01T00:00:00Z"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("result", "before");
        assertThat(result.getOutput()).containsEntry("isBefore", true);
        assertThat(result.getOutput()).containsEntry("isAfter", false);
        assertThat(result.getOutput()).containsEntry("isEqual", false);
    }

    @Test
    void execute_compareAfter_returnsAfterResult() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "calculate",
                "operation", "compare",
                "date1", "2024-06-01T00:00:00Z",
                "date2", "2024-01-01T00:00:00Z"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("result", "after");
        assertThat(result.getOutput()).containsEntry("isAfter", true);
    }

    @Test
    void execute_compareEqual_returnsEqualResult() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "calculate",
                "operation", "compare",
                "date1", "2024-06-01T00:00:00Z",
                "date2", "2024-06-01T00:00:00Z"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("result", "equal");
        assertThat(result.getOutput()).containsEntry("isEqual", true);
    }

    // ========== Calculate: Extract ==========

    @Test
    void execute_extractParts_returnsAllComponents() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "calculate",
                "operation", "extract",
                "date", "2024-03-15T14:30:45Z",
                "timezone", "UTC"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("year", 2024);
        assertThat(result.getOutput()).containsEntry("month", 3);
        assertThat(result.getOutput()).containsEntry("day", 15);
        assertThat(result.getOutput()).containsEntry("hour", 14);
        assertThat(result.getOutput()).containsEntry("minute", 30);
        assertThat(result.getOutput()).containsEntry("second", 45);
        // 2024-03-15 is a Friday = 5
        assertThat(result.getOutput()).containsEntry("dayOfWeek", 5);
        assertThat(result.getOutput()).containsEntry("dayOfWeekName", "FRIDAY");
        assertThat(result.getOutput()).containsKey("dayOfYear");
        assertThat(result.getOutput()).containsKey("weekOfYear");
        assertThat(result.getOutput()).containsEntry("quarter", 1);
    }

    @Test
    void execute_extractParts_q4Date_returnsQuarter4() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "calculate",
                "operation", "extract",
                "date", "2024-11-20T00:00:00Z",
                "timezone", "UTC"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("quarter", 4);
        assertThat(result.getOutput()).containsEntry("month", 11);
    }

    // ========== Calculate: StartOf ==========

    @Test
    void execute_startOfDay_returnsStartOfDay() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "calculate",
                "operation", "startOf",
                "date", "2024-06-15T14:30:00Z",
                "period", "day",
                "timezone", "UTC"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("date").toString()).isEqualTo("2024-06-15");
        assertThat(result.getOutput().get("time").toString()).isEqualTo("00:00");
    }

    @Test
    void execute_startOfMonth_returnsFirstDayOfMonth() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "calculate",
                "operation", "startOf",
                "date", "2024-06-15T14:30:00Z",
                "period", "month",
                "timezone", "UTC"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("date").toString()).isEqualTo("2024-06-01");
    }

    @Test
    void execute_startOfYear_returnsJanFirst() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "calculate",
                "operation", "startOf",
                "date", "2024-06-15T14:30:00Z",
                "period", "year",
                "timezone", "UTC"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("date").toString()).isEqualTo("2024-01-01");
    }

    // ========== Calculate: EndOf ==========

    @Test
    void execute_endOfDay_returnsEndOfDay() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "calculate",
                "operation", "endOf",
                "date", "2024-06-15T10:00:00Z",
                "period", "day",
                "timezone", "UTC"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("date").toString()).isEqualTo("2024-06-15");
        assertThat(result.getOutput().get("time").toString()).contains("23:59:59");
    }

    @Test
    void execute_endOfMonth_returnsLastDayOfMonth() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "calculate",
                "operation", "endOf",
                "date", "2024-06-15T10:00:00Z",
                "period", "month",
                "timezone", "UTC"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        // June has 30 days
        assertThat(result.getOutput().get("date").toString()).isEqualTo("2024-06-30");
    }

    @Test
    void execute_endOfMonthFebruaryLeapYear_returns29th() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "calculate",
                "operation", "endOf",
                "date", "2024-02-10T10:00:00Z",
                "period", "month",
                "timezone", "UTC"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        // 2024 is leap year, Feb has 29 days
        assertThat(result.getOutput().get("date").toString()).isEqualTo("2024-02-29");
    }

    // ========== Validation Errors ==========

    @Test
    void execute_missingResource_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of("operation", "now"));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Resource not selected");
    }

    @Test
    void execute_missingOperation_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of("resource", "current"));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Operation not selected");
    }

    @Test
    void execute_unknownResource_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "unknown",
                "operation", "now"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Unknown resource");
    }

    @Test
    void execute_unknownOperation_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of(
                "resource", "current",
                "operation", "unknownOp"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Unknown operation");
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_containsMultiOperationMarker() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsEntry("x-multi-operation", true);
        assertThat(schema).containsKey("properties");
    }

    @Test
    void getInterfaceDefinition_hasInputsAndOutputs() {
        var iface = handler.getInterfaceDefinition();
        assertThat(iface).containsKey("inputs");
        assertThat(iface).containsKey("outputs");
    }

    // ========== Helper ==========

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        Map<String, Object> nodeConfig = new HashMap<>(config);
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("datetime-1")
                .nodeType("datetime")
                .nodeConfig(nodeConfig)
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
