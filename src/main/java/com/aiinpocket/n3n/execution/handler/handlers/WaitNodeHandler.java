package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Handler for wait/delay nodes.
 * Pauses execution for a specified duration.
 */
@Component
@Slf4j
public class WaitNodeHandler extends AbstractNodeHandler {

    private static final long MAX_WAIT_MS = TimeUnit.HOURS.toMillis(24); // Max 24 hours
    private static final long DEFAULT_WAIT_MS = 1000; // 1 second default

    @Override
    public String getType() {
        return "wait";
    }

    @Override
    public String getDisplayName() {
        return "Wait";
    }

    @Override
    public String getDescription() {
        return "Pauses workflow execution for a specified duration.";
    }

    @Override
    public String getCategory() {
        return "Flow Control";
    }

    @Override
    public String getIcon() {
        return "clock";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        Map<String, Object> inputData = context.getInputData();

        long waitMs = calculateWaitTime(context);

        // Enforce maximum wait time
        if (waitMs > MAX_WAIT_MS) {
            log.warn("Wait time {}ms exceeds maximum {}ms, capping", waitMs, MAX_WAIT_MS);
            waitMs = MAX_WAIT_MS;
        }

        if (waitMs < 0) {
            waitMs = DEFAULT_WAIT_MS;
        }

        log.debug("Wait node sleeping for {}ms", waitMs);

        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return NodeExecutionResult.failure("Wait interrupted: " + e.getMessage());
        }

        // Pass through input data with wait metadata
        Map<String, Object> output = inputData != null ? new HashMap<>(inputData) : new HashMap<>();
        output.put("_waitInfo", Map.of(
            "waitedMs", waitMs,
            "resumedAt", System.currentTimeMillis()
        ));

        return NodeExecutionResult.success(output);
    }

    private long calculateWaitTime(NodeExecutionContext context) {
        String unit = getStringConfig(context, "unit", "seconds");
        int amount = getIntConfig(context, "amount", 1);

        switch (unit.toLowerCase()) {
            case "milliseconds":
            case "ms":
                return amount;
            case "seconds":
            case "s":
                return TimeUnit.SECONDS.toMillis(amount);
            case "minutes":
            case "m":
                return TimeUnit.MINUTES.toMillis(amount);
            case "hours":
            case "h":
                return TimeUnit.HOURS.toMillis(amount);
            default:
                log.warn("Unknown time unit '{}', defaulting to seconds", unit);
                return TimeUnit.SECONDS.toMillis(amount);
        }
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        ValidationResult result = super.validateConfig(config);

        Object amount = config.get("amount");
        if (amount != null) {
            try {
                int amountInt = amount instanceof Number ?
                    ((Number) amount).intValue() : Integer.parseInt(amount.toString());
                if (amountInt < 0) {
                    return ValidationResult.invalid("amount", "Wait amount cannot be negative");
                }
            } catch (NumberFormatException e) {
                return ValidationResult.invalid("amount", "Invalid wait amount: " + amount);
            }
        }

        return result;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "amount", Map.of(
                    "type", "integer",
                    "title", "Amount",
                    "description", "Time to wait",
                    "default", 1,
                    "minimum", 0
                ),
                "unit", Map.of(
                    "type", "string",
                    "title", "Unit",
                    "enum", List.of("milliseconds", "seconds", "minutes", "hours"),
                    "default", "seconds"
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "any")
            )
        );
    }
}
