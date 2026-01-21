package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler for scheduled/cron trigger nodes.
 * These nodes start workflow execution based on a schedule.
 */
@Component
@Slf4j
public class ScheduleTriggerHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "scheduleTrigger";
    }

    @Override
    public String getDisplayName() {
        return "Schedule Trigger";
    }

    @Override
    public String getDescription() {
        return "Triggers workflow execution based on a cron schedule.";
    }

    @Override
    public String getCategory() {
        return "Triggers";
    }

    @Override
    public String getIcon() {
        return "calendar";
    }

    @Override
    public boolean isTrigger() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        // Schedule triggers don't execute like regular nodes
        // They provide context about when/why they were triggered

        String scheduleType = getStringConfig(context, "scheduleType", "cron");
        String cronExpression = getStringConfig(context, "cronExpression", "");
        String timezone = getStringConfig(context, "timezone", "UTC");

        LocalDateTime now = LocalDateTime.now(ZoneId.of(timezone));

        Map<String, Object> output = new HashMap<>();
        output.put("triggeredAt", now.format(DateTimeFormatter.ISO_DATE_TIME));
        output.put("timezone", timezone);
        output.put("scheduleType", scheduleType);

        if (!cronExpression.isEmpty()) {
            output.put("cronExpression", cronExpression);

            // Calculate next execution time
            try {
                CronExpression cron = CronExpression.parse(cronExpression);
                LocalDateTime next = cron.next(now);
                if (next != null) {
                    output.put("nextExecution", next.format(DateTimeFormatter.ISO_DATE_TIME));
                }
            } catch (Exception e) {
                log.warn("Failed to parse cron expression: {}", cronExpression);
            }
        }

        // Include any static payload configured
        Object payload = context.getNodeConfig().get("payload");
        if (payload != null) {
            output.put("payload", payload);
        }

        return NodeExecutionResult.success(output);
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        String scheduleType = (String) config.getOrDefault("scheduleType", "cron");

        if ("cron".equals(scheduleType)) {
            Object cronExpr = config.get("cronExpression");
            if (cronExpr == null || cronExpr.toString().trim().isEmpty()) {
                return ValidationResult.invalid("cronExpression", "Cron expression is required");
            }

            try {
                CronExpression.parse(cronExpr.toString());
            } catch (IllegalArgumentException e) {
                return ValidationResult.invalid("cronExpression", "Invalid cron expression: " + e.getMessage());
            }
        } else if ("interval".equals(scheduleType)) {
            Object interval = config.get("interval");
            if (interval == null) {
                return ValidationResult.invalid("interval", "Interval is required");
            }
            try {
                int intervalValue = interval instanceof Number ?
                    ((Number) interval).intValue() : Integer.parseInt(interval.toString());
                if (intervalValue < 1) {
                    return ValidationResult.invalid("interval", "Interval must be at least 1");
                }
            } catch (NumberFormatException e) {
                return ValidationResult.invalid("interval", "Invalid interval value");
            }
        }

        // Validate timezone
        Object timezone = config.get("timezone");
        if (timezone != null) {
            try {
                ZoneId.of(timezone.toString());
            } catch (Exception e) {
                return ValidationResult.invalid("timezone", "Invalid timezone: " + timezone);
            }
        }

        return ValidationResult.valid();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "scheduleType", Map.of(
                    "type", "string",
                    "title", "Schedule Type",
                    "enum", List.of("cron", "interval"),
                    "default", "cron",
                    "description", "Type of schedule"
                ),
                "cronExpression", Map.of(
                    "type", "string",
                    "title", "Cron Expression",
                    "description", "Cron expression (e.g., '0 0 * * *' for daily at midnight)",
                    "examples", List.of("0 0 * * *", "0 */5 * * *", "0 9 * * MON-FRI")
                ),
                "interval", Map.of(
                    "type", "integer",
                    "title", "Interval",
                    "description", "Interval value (for interval schedule type)",
                    "minimum", 1
                ),
                "intervalUnit", Map.of(
                    "type", "string",
                    "title", "Interval Unit",
                    "enum", List.of("seconds", "minutes", "hours", "days"),
                    "default", "minutes"
                ),
                "timezone", Map.of(
                    "type", "string",
                    "title", "Timezone",
                    "default", "UTC",
                    "description", "Timezone for schedule (e.g., 'America/New_York', 'Asia/Tokyo')"
                ),
                "enabled", Map.of(
                    "type", "boolean",
                    "title", "Enabled",
                    "default", true,
                    "description", "Whether the schedule is active"
                ),
                "payload", Map.of(
                    "type", "object",
                    "title", "Static Payload",
                    "description", "Static data to include in trigger output"
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(),  // Triggers have no inputs
            "outputs", List.of(
                Map.of("name", "output", "type", "object")
            )
        );
    }
}
