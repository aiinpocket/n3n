package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis string operations: get, set, mget, mset, incr, append.
 */
final class RedisStringOperations {

    private RedisStringOperations() {}

    static NodeExecutionResult execute(
        RedisCommands<String, String> commands,
        String operation,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) throws Exception {
        return switch (operation) {
            case "get" -> {
                String key = getRequiredParam(params, "key");
                String value = commands.get(key);
                yield NodeExecutionResult.success(Map.of(
                    "value", value != null ? value : "",
                    "exists", value != null
                ));
            }
            case "set" -> {
                String key = getRequiredParam(params, "key");
                String value = getRequiredParam(params, "value");
                int ttl = getIntParam(params, "ttl", 0);

                if (ttl > 0) {
                    commands.setex(key, ttl, value);
                } else {
                    commands.set(key, value);
                }
                yield NodeExecutionResult.success(Map.of("success", true));
            }
            case "mget" -> {
                String keysJson = getRequiredParam(params, "keys");
                List<String> keys = objectMapper.readValue(keysJson, new TypeReference<>() {});

                List<String> values = commands.mget(keys.toArray(new String[0]))
                    .stream()
                    .map(kv -> kv.hasValue() ? kv.getValue() : null)
                    .toList();

                Map<String, String> result = new LinkedHashMap<>();
                for (int i = 0; i < keys.size(); i++) {
                    result.put(keys.get(i), i < values.size() ? values.get(i) : null);
                }
                yield NodeExecutionResult.success(Map.of("values", result));
            }
            case "mset" -> {
                String dataJson = getRequiredParam(params, "data");
                Map<String, String> data = objectMapper.readValue(dataJson, new TypeReference<>() {});

                commands.mset(data);
                yield NodeExecutionResult.success(Map.of("success", true));
            }
            case "incr" -> {
                String key = getRequiredParam(params, "key");
                int amount = getIntParam(params, "amount", 1);

                long value = commands.incrby(key, amount);
                yield NodeExecutionResult.success(Map.of("value", value));
            }
            case "append" -> {
                String key = getRequiredParam(params, "key");
                String value = getRequiredParam(params, "value");

                long length = commands.append(key, value);
                yield NodeExecutionResult.success(Map.of("length", length));
            }
            default -> NodeExecutionResult.failure("Unknown string operation: " + operation);
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
