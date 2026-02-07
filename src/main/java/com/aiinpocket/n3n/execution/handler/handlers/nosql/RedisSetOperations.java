package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.sync.RedisCommands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redis set operations: sadd, srem, smembers, sismember, scard.
 */
final class RedisSetOperations {

    private RedisSetOperations() {}

    static NodeExecutionResult execute(
        RedisCommands<String, String> commands,
        String operation,
        Map<String, Object> params,
        ObjectMapper objectMapper
    ) throws Exception {
        return switch (operation) {
            case "sadd" -> {
                String key = getRequiredParam(params, "key");
                String membersJson = getRequiredParam(params, "members");
                List<String> members = objectMapper.readValue(membersJson, new TypeReference<>() {});

                long added = commands.sadd(key, members.toArray(new String[0]));
                yield NodeExecutionResult.success(Map.of("addedCount", added));
            }
            case "srem" -> {
                String key = getRequiredParam(params, "key");
                String membersJson = getRequiredParam(params, "members");
                List<String> members = objectMapper.readValue(membersJson, new TypeReference<>() {});

                long removed = commands.srem(key, members.toArray(new String[0]));
                yield NodeExecutionResult.success(Map.of("removedCount", removed));
            }
            case "smembers" -> {
                String key = getRequiredParam(params, "key");

                Set<String> members = commands.smembers(key);
                yield NodeExecutionResult.success(Map.of(
                    "members", new ArrayList<>(members),
                    "size", members.size()
                ));
            }
            case "sismember" -> {
                String key = getRequiredParam(params, "key");
                String member = getRequiredParam(params, "member");

                boolean isMember = commands.sismember(key, member);
                yield NodeExecutionResult.success(Map.of("isMember", isMember));
            }
            case "scard" -> {
                String key = getRequiredParam(params, "key");

                long size = commands.scard(key);
                yield NodeExecutionResult.success(Map.of("size", size));
            }
            default -> NodeExecutionResult.failure("Unknown set operation: " + operation);
        };
    }

    private static String getRequiredParam(Map<String, Object> params, String name) {
        Object value = params.get(name);
        if (value == null || (value instanceof String && ((String) value).isEmpty())) {
            throw new IllegalArgumentException("Required parameter '" + name + "' is missing");
        }
        return value.toString();
    }
}
