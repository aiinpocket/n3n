package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Handler for retry nodes.
 * Wraps execution logic with retry capabilities including
 * configurable max retries, backoff strategies, and error filtering.
 */
@Component
@Slf4j
public class RetryNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "retry";
    }

    @Override
    public String getDisplayName() {
        return "Retry";
    }

    @Override
    public String getDescription() {
        return "Retries failed operations with configurable backoff strategy.";
    }

    @Override
    public String getCategory() {
        return "Flow Control";
    }

    @Override
    public String getIcon() {
        return "reload";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        int maxRetries = getIntConfig(context, "maxRetries", 3);
        int initialDelayMs = getIntConfig(context, "initialDelayMs", 1000);
        String backoffStrategy = getStringConfig(context, "backoffStrategy", "exponential");
        double backoffMultiplier = getDoubleConfig(context, "backoffMultiplier", 2.0);
        int maxDelayMs = getIntConfig(context, "maxDelayMs", 30000);
        boolean retryOnTimeout = getBooleanConfig(context, "retryOnTimeout", true);

        // The retry node passes through data and records retry metadata
        // In actual flow execution, the engine handles retry logic
        // This node provides configuration and tracking

        Map<String, Object> inputData = context.getInputData();
        boolean hasError = inputData != null && inputData.containsKey("_error");

        if (hasError) {
            // Simulate retry behavior
            int currentAttempt = 1;
            if (inputData.containsKey("_retryAttempt")) {
                currentAttempt = ((Number) inputData.get("_retryAttempt")).intValue() + 1;
            }

            if (currentAttempt > maxRetries) {
                return NodeExecutionResult.failure(
                    "Max retries (" + maxRetries + ") exceeded. Last error: " +
                    inputData.getOrDefault("_error", "Unknown error"));
            }

            // Calculate delay
            long delay = calculateDelay(currentAttempt, initialDelayMs, backoffStrategy,
                                        backoffMultiplier, maxDelayMs);

            log.debug("Retry attempt {}/{} with {}ms delay ({})",
                currentAttempt, maxRetries, delay, backoffStrategy);

            if (delay > 0) {
                try {
                    Thread.sleep(Math.min(delay, 30000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return NodeExecutionResult.failure("Retry interrupted");
                }
            }

            Map<String, Object> output = new LinkedHashMap<>();
            if (inputData != null) {
                inputData.forEach((k, v) -> {
                    if (!k.startsWith("_error") && !k.startsWith("_retry")) {
                        output.put(k, v);
                    }
                });
            }
            output.put("_retryAttempt", currentAttempt);
            output.put("_retryMax", maxRetries);
            output.put("_retryDelay", delay);
            output.put("_retryStrategy", backoffStrategy);

            return NodeExecutionResult.success(output);
        }

        // No error - pass through with retry metadata
        Map<String, Object> output = new LinkedHashMap<>();
        if (inputData != null) {
            output.putAll(inputData);
        }
        output.put("_retryConfig", Map.of(
            "maxRetries", maxRetries,
            "initialDelayMs", initialDelayMs,
            "backoffStrategy", backoffStrategy,
            "backoffMultiplier", backoffMultiplier,
            "maxDelayMs", maxDelayMs
        ));

        return NodeExecutionResult.success(output);
    }

    private long calculateDelay(int attempt, int initialDelay, String strategy,
                                double multiplier, int maxDelay) {
        long delay = switch (strategy) {
            case "linear" -> (long) initialDelay * attempt;
            case "exponential" -> (long) (initialDelay * Math.pow(multiplier, attempt - 1));
            case "fixed" -> initialDelay;
            case "jitter" -> {
                long base = (long) (initialDelay * Math.pow(multiplier, attempt - 1));
                long jitter = (long) (base * Math.random());
                yield base + jitter;
            }
            default -> (long) (initialDelay * Math.pow(multiplier, attempt - 1));
        };

        return Math.min(delay, maxDelay);
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("maxRetries", Map.of(
            "type", "integer", "title", "Max Retries",
            "description", "Maximum number of retry attempts",
            "default", 3, "minimum", 1, "maximum", 10
        ));
        properties.put("initialDelayMs", Map.of(
            "type", "integer", "title", "Initial Delay (ms)",
            "description", "Delay before the first retry",
            "default", 1000
        ));
        properties.put("backoffStrategy", Map.of(
            "type", "string", "title", "Backoff Strategy",
            "enum", List.of("fixed", "linear", "exponential", "jitter"),
            "default", "exponential"
        ));
        properties.put("backoffMultiplier", Map.of(
            "type", "number", "title", "Backoff Multiplier",
            "default", 2.0
        ));
        properties.put("maxDelayMs", Map.of(
            "type", "integer", "title", "Max Delay (ms)",
            "default", 30000
        ));
        properties.put("retryOnTimeout", Map.of(
            "type", "boolean", "title", "Retry on Timeout",
            "default", true
        ));

        return Map.of(
            "type", "object",
            "properties", properties
        );
    }
}
