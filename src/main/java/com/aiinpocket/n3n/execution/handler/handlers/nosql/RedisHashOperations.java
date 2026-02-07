package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.List;
import java.util.Map;

/**
 * Redis hash operations: hget, hset, hmset, hgetall, hdel, hincrby.
 */
final class RedisHashOperations {

    private RedisHashOperations() {}

    static NodeExecutionResult execute(
        RedisCommands<String, String> commands,
        String operation,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) throws Exception {
        return switch (operation) {
            case "hget" -> {
                String key = getRequiredParam(params, "key");
                String field = getRequiredParam(params, "field");

                String value = commands.hget(key, field);
                yield NodeExecutionResult.success(Map.of(
                    "value", value != null ? value : "",
                    "exists", value != null
                ));
            }
            case "hset" -> {
                String key = getRequiredParam(params, "key");
                String field = getRequiredParam(params, "field");
                String value = getRequiredParam(params, "value");

                boolean created = commands.hset(key, field, value);
                yield NodeExecutionResult.success(Map.of("created", created));
            }
            case "hmset" -> {
                String key = getRequiredParam(params, "key");
                String dataJson = getRequiredParam(params, "data");
                Map<String, String> data = objectMapper.readValue(dataJson, new TypeReference<>() {});

                commands.hset(key, data);
                yield NodeExecutionResult.success(Map.of("success", true));
            }
            case "hgetall" -> {
                String key = getRequiredParam(params, "key");

                Map<String, String> data = commands.hgetall(key);
                yield NodeExecutionResult.success(Map.of("data", data));
            }
            case "hdel" -> {
                String key = getRequiredParam(params, "key");
                String fieldsJson = getRequiredParam(params, "fields");
                List<String> fields = objectMapper.readValue(fieldsJson, new TypeReference<>() {});

                long deleted = commands.hdel(key, fields.toArray(new String[0]));
                yield NodeExecutionResult.success(Map.of("deletedCount", deleted));
            }
            case "hincrby" -> {
                String key = getRequiredParam(params, "key");
                String field = getRequiredParam(params, "field");
                int amount = getIntParam(params, "amount", 1);

                long value = commands.hincrby(key, field, amount);
                yield NodeExecutionResult.success(Map.of("value", value));
            }
            default -> NodeExecutionResult.failure("Unknown hash operation: " + operation);
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
