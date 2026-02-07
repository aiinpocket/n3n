package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.List;
import java.util.Map;

/**
 * Redis list operations: lpush, rpush, lpop, rpop, lrange, llen.
 */
final class RedisListOperations {

    private RedisListOperations() {}

    static NodeExecutionResult execute(
        RedisCommands<String, String> commands,
        String operation,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) throws Exception {
        return switch (operation) {
            case "lpush" -> {
                String key = getRequiredParam(params, "key");
                String valuesJson = getRequiredParam(params, "values");
                List<String> values = objectMapper.readValue(valuesJson, new TypeReference<>() {});

                long length = commands.lpush(key, values.toArray(new String[0]));
                yield NodeExecutionResult.success(Map.of("length", length));
            }
            case "rpush" -> {
                String key = getRequiredParam(params, "key");
                String valuesJson = getRequiredParam(params, "values");
                List<String> values = objectMapper.readValue(valuesJson, new TypeReference<>() {});

                long length = commands.rpush(key, values.toArray(new String[0]));
                yield NodeExecutionResult.success(Map.of("length", length));
            }
            case "lpop" -> {
                String key = getRequiredParam(params, "key");
                int count = getIntParam(params, "count", 1);

                List<String> values = commands.lpop(key, count);
                yield NodeExecutionResult.success(Map.of("values", values != null ? values : List.of()));
            }
            case "rpop" -> {
                String key = getRequiredParam(params, "key");
                int count = getIntParam(params, "count", 1);

                List<String> values = commands.rpop(key, count);
                yield NodeExecutionResult.success(Map.of("values", values != null ? values : List.of()));
            }
            case "lrange" -> {
                String key = getRequiredParam(params, "key");
                int start = getIntParam(params, "start", 0);
                int stop = getIntParam(params, "stop", -1);

                List<String> values = commands.lrange(key, start, stop);
                yield NodeExecutionResult.success(Map.of(
                    "values", values,
                    "length", values.size()
                ));
            }
            case "llen" -> {
                String key = getRequiredParam(params, "key");

                long length = commands.llen(key);
                yield NodeExecutionResult.success(Map.of("length", length));
            }
            default -> NodeExecutionResult.failure("Unknown list operation: " + operation);
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
