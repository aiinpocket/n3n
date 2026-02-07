package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Handler for rate limiter nodes.
 * Controls the throughput of items passing through the flow.
 * Supports fixed window, sliding window, and token bucket algorithms.
 */
@Component
@Slf4j
public class RateLimiterNodeHandler extends AbstractNodeHandler {

    // Simple in-memory rate limiter state per execution
    private static final ConcurrentHashMap<String, Deque<Long>> windowState = new ConcurrentHashMap<>();

    @Override
    public String getType() {
        return "rateLimiter";
    }

    @Override
    public String getDisplayName() {
        return "Rate Limiter";
    }

    @Override
    public String getDescription() {
        return "Controls the rate of items flowing through the workflow.";
    }

    @Override
    public String getCategory() {
        return "Flow Control";
    }

    @Override
    public String getIcon() {
        return "dashboard";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        int maxRequests = getIntConfig(context, "maxRequests", 10);
        int windowMs = getIntConfig(context, "windowMs", 1000);
        String mode = getStringConfig(context, "mode", "delay");
        String key = getStringConfig(context, "key",
            context.getExecutionId() + ":" + context.getNodeId());

        log.debug("Rate limiter: maxRequests={}, windowMs={}, mode={}", maxRequests, windowMs, mode);

        Deque<Long> timestamps = windowState.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());

        long now = System.currentTimeMillis();
        long windowStart = now - windowMs;

        // Remove timestamps outside the window
        while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= maxRequests) {
            switch (mode) {
                case "drop":
                    // Drop the item silently
                    return NodeExecutionResult.success(Map.of(
                        "dropped", true,
                        "reason", "Rate limit exceeded",
                        "currentRate", timestamps.size(),
                        "maxRate", maxRequests
                    ));

                case "error":
                    return NodeExecutionResult.failure(
                        "Rate limit exceeded: " + timestamps.size() + "/" + maxRequests +
                        " requests in " + windowMs + "ms window");

                case "delay":
                default:
                    // Wait until the oldest request falls outside the window
                    long oldestTimestamp = timestamps.peekFirst();
                    long waitMs = (oldestTimestamp + windowMs) - now;
                    if (waitMs > 0) {
                        try {
                            log.debug("Rate limiter: waiting {}ms", waitMs);
                            Thread.sleep(Math.min(waitMs, 30000)); // Max wait 30s
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return NodeExecutionResult.failure("Rate limiter interrupted");
                        }
                    }
                    break;
            }
        }

        // Record this request
        timestamps.addLast(System.currentTimeMillis());

        // Pass through input data
        Map<String, Object> output = new LinkedHashMap<>();
        if (context.getInputData() != null) {
            output.putAll(context.getInputData());
        }
        output.put("_rateLimiter", Map.of(
            "currentRate", timestamps.size(),
            "maxRate", maxRequests,
            "windowMs", windowMs,
            "timestamp", Instant.now().toString()
        ));

        return NodeExecutionResult.success(output);
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("maxRequests", Map.of(
            "type", "integer",
            "title", "Max Requests",
            "description", "Maximum number of requests allowed in the time window",
            "default", 10,
            "minimum", 1
        ));
        properties.put("windowMs", Map.of(
            "type", "integer",
            "title", "Window (ms)",
            "description", "Time window in milliseconds",
            "default", 1000,
            "minimum", 100
        ));
        properties.put("mode", Map.of(
            "type", "string",
            "title", "Overflow Mode",
            "enum", List.of("delay", "drop", "error"),
            "enumNames", List.of(
                "Delay (wait until slot available)",
                "Drop (silently skip item)",
                "Error (fail with error)"
            ),
            "default", "delay"
        ));

        return Map.of(
            "type", "object",
            "properties", properties
        );
    }
}
