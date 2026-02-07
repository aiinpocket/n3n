package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.List;
import java.util.Map;

/**
 * Redis key management operations: del, exists, expire, ttl, keys, type.
 */
final class RedisKeyOperations {

    private RedisKeyOperations() {}

    static NodeExecutionResult execute(
        RedisCommands<String, String> commands,
        String operation,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) throws Exception {
        return switch (operation) {
            case "del" -> {
                String keysJson = getRequiredParam(params, "keys");
                List<String> keys = objectMapper.readValue(keysJson, new TypeReference<>() {});

                long deleted = commands.del(keys.toArray(new String[0]));
                yield NodeExecutionResult.success(Map.of("deletedCount", deleted));
            }
            case "exists" -> {
                String keysJson = getRequiredParam(params, "keys");
                List<String> keys = objectMapper.readValue(keysJson, new TypeReference<>() {});

                long exists = commands.exists(keys.toArray(new String[0]));
                yield NodeExecutionResult.success(Map.of("existsCount", exists));
            }
            case "expire" -> {
                String key = getRequiredParam(params, "key");
                int seconds = getIntParam(params, "seconds", 0);

                boolean success = commands.expire(key, seconds);
                yield NodeExecutionResult.success(Map.of("success", success));
            }
            case "ttl" -> {
                String key = getRequiredParam(params, "key");

                long ttl = commands.ttl(key);
                yield NodeExecutionResult.success(Map.of("ttl", ttl));
            }
            case "keys" -> {
                String pattern = getRequiredParam(params, "pattern");
                int limit = getIntParam(params, "limit", 100);

                List<String> keys = commands.keys(pattern);
                if (keys.size() > limit) {
                    keys = keys.subList(0, limit);
                }
                yield NodeExecutionResult.success(Map.of(
                    "keys", keys,
                    "count", keys.size()
                ));
            }
            case "type" -> {
                String key = getRequiredParam(params, "key");

                String type = commands.type(key);
                yield NodeExecutionResult.success(Map.of("type", type));
            }
            default -> NodeExecutionResult.failure("Unknown key operation: " + operation);
        };
    }

    private static String getRequiredParam(Map<String, Object> params, String name) {
        Object value = params.get(name);
        if (value == null || (value instanceof String && ((String) value).isEmpty())) {
            throw new IllegalArgumentException("Required parameter '" + name + "' is missing");
        }
        return value.toString();
    }

    private static int getIntParam(Map<String, Object> params, String name, int defaultValue) {
        Object value = params.get(name);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
