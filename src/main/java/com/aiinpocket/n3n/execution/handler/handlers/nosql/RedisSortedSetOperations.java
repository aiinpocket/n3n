package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Redis sorted set operations: zadd, zrem, zrange, zscore, zincrby, zcard.
 */
final class RedisSortedSetOperations {

    private RedisSortedSetOperations() {}

    static NodeExecutionResult execute(
        RedisCommands<String, String> commands,
        String operation,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) throws Exception {
        return switch (operation) {
            case "zadd" -> {
                String key = getRequiredParam(params, "key");
                String membersJson = getRequiredParam(params, "members");
                Map<String, Number> members = objectMapper.readValue(membersJson, new TypeReference<>() {});

                long added = 0;
                for (Map.Entry<String, Number> entry : members.entrySet()) {
                    added += commands.zadd(key, entry.getValue().doubleValue(), entry.getKey());
                }
                yield NodeExecutionResult.success(Map.of("addedCount", added));
            }
            case "zrem" -> {
                String key = getRequiredParam(params, "key");
                String membersJson = getRequiredParam(params, "members");
                List<String> members = objectMapper.readValue(membersJson, new TypeReference<>() {});

                long removed = commands.zrem(key, members.toArray(new String[0]));
                yield NodeExecutionResult.success(Map.of("removedCount", removed));
            }
            case "zrange" -> {
                String key = getRequiredParam(params, "key");
                int start = getIntParam(params, "start", 0);
                int stop = getIntParam(params, "stop", -1);
                boolean withScores = getBoolParam(params, "withScores", false);
                boolean reverse = getBoolParam(params, "reverse", false);

                if (withScores) {
                    List<Map<String, Object>> members = new ArrayList<>();
                    if (reverse) {
                        commands.zrevrangeWithScores(key, start, stop).forEach(sv -> {
                            members.add(Map.of("member", sv.getValue(), "score", sv.getScore()));
                        });
                    } else {
                        commands.zrangeWithScores(key, start, stop).forEach(sv -> {
                            members.add(Map.of("member", sv.getValue(), "score", sv.getScore()));
                        });
                    }
                    yield NodeExecutionResult.success(Map.of("members", members));
                } else {
                    List<String> members;
                    if (reverse) {
                        members = commands.zrevrange(key, start, stop);
                    } else {
                        members = commands.zrange(key, start, stop);
                    }
                    yield NodeExecutionResult.success(Map.of("members", members));
                }
            }
            case "zscore" -> {
                String key = getRequiredParam(params, "key");
                String member = getRequiredParam(params, "member");

                Double score = commands.zscore(key, member);
                yield NodeExecutionResult.success(Map.of(
                    "score", score != null ? score : 0,
                    "exists", score != null
                ));
            }
            case "zincrby" -> {
                String key = getRequiredParam(params, "key");
                String member = getRequiredParam(params, "member");
                double amount = getDoubleParam(params, "amount", 1.0);

                double score = commands.zincrby(key, amount, member);
                yield NodeExecutionResult.success(Map.of("score", score));
            }
            case "zcard" -> {
                String key = getRequiredParam(params, "key");

                long size = commands.zcard(key);
                yield NodeExecutionResult.success(Map.of("size", size));
            }
            default -> NodeExecutionResult.failure("Unknown sorted set operation: " + operation);
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

    private static double getDoubleParam(Map<String, Object> params, String name, double defaultValue) {
        Object value = params.get(name);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean getBoolParam(Map<String, Object> params, String name, boolean defaultValue) {
        Object value = params.get(name);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }
}
