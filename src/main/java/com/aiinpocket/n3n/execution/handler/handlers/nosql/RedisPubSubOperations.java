package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.Map;

/**
 * Redis pub/sub operations: publish.
 */
final class RedisPubSubOperations {

    private RedisPubSubOperations() {}

    static NodeExecutionResult execute(
        RedisCommands<String, String> commands,
        String operation,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) {
        if ("publish".equals(operation)) {
            String channel = getRequiredParam(params, "channel");
            String message = getRequiredParam(params, "message");

            long subscriberCount = commands.publish(channel, message);
            return NodeExecutionResult.success(Map.of("subscriberCount", subscriberCount));
        }

        return NodeExecutionResult.failure("Unknown pubsub operation: " + operation);
    }

    private static String getRequiredParam(Map<String, Object> params, String name) {
        Object value = params.get(name);
        if (value == null || (value instanceof String && ((String) value).isEmpty())) {
            throw new IllegalArgumentException("Required parameter '" + name + "' is missing");
        }
        return value.toString();
    }
}
