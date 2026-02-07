package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.multiop.FieldDef;
import com.aiinpocket.n3n.execution.handler.multiop.MultiOperationNodeHandler;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis node handler for key-value store operations.
 *
 * Features:
 * - String operations (get, set, append, incr, decr)
 * - Hash operations (hget, hset, hgetall, hdel)
 * - List operations (lpush, rpush, lpop, rpop, lrange)
 * - Set operations (sadd, srem, smembers, sismember)
 * - Sorted Set operations (zadd, zrem, zrange, zscore)
 * - Key operations (del, exists, expire, ttl, keys)
 * - Pub/Sub (publish only, subscribe is async)
 *
 * Credential schema:
 * - host: Redis host (default: localhost)
 * - port: Redis port (default: 6379)
 * - password: Redis password (optional)
 * - database: Redis database number (default: 1, to avoid conflict with app cache on db 0)
 * - ssl: Enable SSL (default: false)
 * - url: Redis URL (redis://... or rediss://...) - overrides above
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RedisNodeHandler extends MultiOperationNodeHandler {

    private final ObjectMapper objectMapper;

    // Client cache with TTL
    private final ConcurrentHashMap<String, ClientEntry> clients = new ConcurrentHashMap<>();
    private static final long CLIENT_TTL_MS = 5 * 60 * 1000; // 5 minutes

    @Override
    public String getType() {
        return "redis";
    }

    @Override
    public String getDisplayName() {
        return "Redis";
    }

    @Override
    public String getDescription() {
        return "Redis key-value store operations. Strings, hashes, lists, sets, sorted sets, and more.";
    }

    @Override
    public String getCategory() {
        return "Data";
    }

    @Override
    public String getIcon() {
        return "redis";
    }

    @Override
    public String getCredentialType() {
        return "redis";
    }

    @Override
    public Map<String, ResourceDef> getResources() {
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        resources.put("string", ResourceDef.of("string", "String", "String value operations"));
        resources.put("hash", ResourceDef.of("hash", "Hash", "Hash (map) operations"));
        resources.put("list", ResourceDef.of("list", "List", "List operations"));
        resources.put("set", ResourceDef.of("set", "Set", "Set operations"));
        resources.put("sortedSet", ResourceDef.of("sortedSet", "Sorted Set", "Sorted set operations"));
        resources.put("key", ResourceDef.of("key", "Key", "Key management operations"));
        resources.put("pubsub", ResourceDef.of("pubsub", "Pub/Sub", "Publish/Subscribe operations"));
        return resources;
    }

    @Override
    public Map<String, List<OperationDef>> getOperations() {
        Map<String, List<OperationDef>> operations = new LinkedHashMap<>();

        // String operations
        operations.put("string", List.of(
            OperationDef.create("get", "Get")
                .description("Get the value of a key")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Key name")
                        .required()
                ))
                .outputDescription("Returns { value: \"...\", exists: boolean }")
                .build(),

            OperationDef.create("set", "Set")
                .description("Set the value of a key")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Key name")
                        .required(),
                    FieldDef.textarea("value", "Value")
                        .withDescription("Value to set")
                        .required(),
                    FieldDef.integer("ttl", "TTL (seconds)")
                        .withDescription("Expiration time in seconds (0 = no expiration)")
                        .withDefault(0)
                ))
                .outputDescription("Returns { success: boolean }")
                .build(),

            OperationDef.create("mget", "Get Multiple")
                .description("Get values of multiple keys")
                .fields(List.of(
                    FieldDef.textarea("keys", "Keys")
                        .withDescription("Array of key names as JSON")
                        .withPlaceholder("[\"key1\", \"key2\", \"key3\"]")
                        .required()
                ))
                .outputDescription("Returns { values: {...} }")
                .build(),

            OperationDef.create("mset", "Set Multiple")
                .description("Set multiple key-value pairs")
                .fields(List.of(
                    FieldDef.textarea("data", "Data")
                        .withDescription("Key-value pairs as JSON object")
                        .withPlaceholder("{\"key1\": \"value1\", \"key2\": \"value2\"}")
                        .required()
                ))
                .outputDescription("Returns { success: boolean }")
                .build(),

            OperationDef.create("incr", "Increment")
                .description("Increment the integer value of a key")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Key name")
                        .required(),
                    FieldDef.integer("amount", "Amount")
                        .withDescription("Amount to increment (can be negative)")
                        .withDefault(1)
                ))
                .outputDescription("Returns { value: n }")
                .build(),

            OperationDef.create("append", "Append")
                .description("Append a value to a key")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Key name")
                        .required(),
                    FieldDef.string("value", "Value")
                        .withDescription("Value to append")
                        .required()
                ))
                .outputDescription("Returns { length: n }")
                .build()
        ));

        // Hash operations
        operations.put("hash", List.of(
            OperationDef.create("hget", "Get Field")
                .description("Get the value of a hash field")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Hash key name")
                        .required(),
                    FieldDef.string("field", "Field")
                        .withDescription("Field name")
                        .required()
                ))
                .outputDescription("Returns { value: \"...\", exists: boolean }")
                .build(),

            OperationDef.create("hset", "Set Field")
                .description("Set the value of a hash field")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Hash key name")
                        .required(),
                    FieldDef.string("field", "Field")
                        .withDescription("Field name")
                        .required(),
                    FieldDef.textarea("value", "Value")
                        .withDescription("Value to set")
                        .required()
                ))
                .outputDescription("Returns { created: boolean }")
                .build(),

            OperationDef.create("hmset", "Set Multiple Fields")
                .description("Set multiple hash fields")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Hash key name")
                        .required(),
                    FieldDef.textarea("data", "Data")
                        .withDescription("Field-value pairs as JSON object")
                        .withPlaceholder("{\"field1\": \"value1\", \"field2\": \"value2\"}")
                        .required()
                ))
                .outputDescription("Returns { success: boolean }")
                .build(),

            OperationDef.create("hgetall", "Get All Fields")
                .description("Get all fields and values in a hash")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Hash key name")
                        .required()
                ))
                .outputDescription("Returns { data: {...} }")
                .build(),

            OperationDef.create("hdel", "Delete Field")
                .description("Delete one or more hash fields")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Hash key name")
                        .required(),
                    FieldDef.textarea("fields", "Fields")
                        .withDescription("Field names as JSON array")
                        .withPlaceholder("[\"field1\", \"field2\"]")
                        .required()
                ))
                .outputDescription("Returns { deletedCount: n }")
                .build(),

            OperationDef.create("hincrby", "Increment Field")
                .description("Increment the integer value of a hash field")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Hash key name")
                        .required(),
                    FieldDef.string("field", "Field")
                        .withDescription("Field name")
                        .required(),
                    FieldDef.integer("amount", "Amount")
                        .withDescription("Amount to increment")
                        .withDefault(1)
                ))
                .outputDescription("Returns { value: n }")
                .build()
        ));

        // List operations
        operations.put("list", List.of(
            OperationDef.create("lpush", "Push Left")
                .description("Insert values at the beginning of a list")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("List key name")
                        .required(),
                    FieldDef.textarea("values", "Values")
                        .withDescription("Values to push as JSON array")
                        .withPlaceholder("[\"value1\", \"value2\"]")
                        .required()
                ))
                .outputDescription("Returns { length: n }")
                .build(),

            OperationDef.create("rpush", "Push Right")
                .description("Insert values at the end of a list")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("List key name")
                        .required(),
                    FieldDef.textarea("values", "Values")
                        .withDescription("Values to push as JSON array")
                        .withPlaceholder("[\"value1\", \"value2\"]")
                        .required()
                ))
                .outputDescription("Returns { length: n }")
                .build(),

            OperationDef.create("lpop", "Pop Left")
                .description("Remove and return the first element")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("List key name")
                        .required(),
                    FieldDef.integer("count", "Count")
                        .withDescription("Number of elements to pop")
                        .withDefault(1)
                ))
                .outputDescription("Returns { values: [...] }")
                .build(),

            OperationDef.create("rpop", "Pop Right")
                .description("Remove and return the last element")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("List key name")
                        .required(),
                    FieldDef.integer("count", "Count")
                        .withDescription("Number of elements to pop")
                        .withDefault(1)
                ))
                .outputDescription("Returns { values: [...] }")
                .build(),

            OperationDef.create("lrange", "Get Range")
                .description("Get a range of elements from a list")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("List key name")
                        .required(),
                    FieldDef.integer("start", "Start")
                        .withDescription("Start index (0-based, negative from end)")
                        .withDefault(0),
                    FieldDef.integer("stop", "Stop")
                        .withDescription("Stop index (-1 for all)")
                        .withDefault(-1)
                ))
                .outputDescription("Returns { values: [...], length: n }")
                .build(),

            OperationDef.create("llen", "Get Length")
                .description("Get the length of a list")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("List key name")
                        .required()
                ))
                .outputDescription("Returns { length: n }")
                .build()
        ));

        // Set operations
        operations.put("set", List.of(
            OperationDef.create("sadd", "Add Members")
                .description("Add members to a set")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Set key name")
                        .required(),
                    FieldDef.textarea("members", "Members")
                        .withDescription("Members to add as JSON array")
                        .withPlaceholder("[\"member1\", \"member2\"]")
                        .required()
                ))
                .outputDescription("Returns { addedCount: n }")
                .build(),

            OperationDef.create("srem", "Remove Members")
                .description("Remove members from a set")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Set key name")
                        .required(),
                    FieldDef.textarea("members", "Members")
                        .withDescription("Members to remove as JSON array")
                        .withPlaceholder("[\"member1\", \"member2\"]")
                        .required()
                ))
                .outputDescription("Returns { removedCount: n }")
                .build(),

            OperationDef.create("smembers", "Get All Members")
                .description("Get all members in a set")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Set key name")
                        .required()
                ))
                .outputDescription("Returns { members: [...], size: n }")
                .build(),

            OperationDef.create("sismember", "Is Member")
                .description("Check if a value is a member of a set")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Set key name")
                        .required(),
                    FieldDef.string("member", "Member")
                        .withDescription("Value to check")
                        .required()
                ))
                .outputDescription("Returns { isMember: boolean }")
                .build(),

            OperationDef.create("scard", "Get Size")
                .description("Get the number of members in a set")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Set key name")
                        .required()
                ))
                .outputDescription("Returns { size: n }")
                .build()
        ));

        // Sorted Set operations
        operations.put("sortedSet", List.of(
            OperationDef.create("zadd", "Add Members")
                .description("Add members with scores to a sorted set")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Sorted set key name")
                        .required(),
                    FieldDef.textarea("members", "Members")
                        .withDescription("Members with scores as JSON object")
                        .withPlaceholder("{\"member1\": 1.0, \"member2\": 2.0}")
                        .required()
                ))
                .outputDescription("Returns { addedCount: n }")
                .build(),

            OperationDef.create("zrem", "Remove Members")
                .description("Remove members from a sorted set")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Sorted set key name")
                        .required(),
                    FieldDef.textarea("members", "Members")
                        .withDescription("Members to remove as JSON array")
                        .withPlaceholder("[\"member1\", \"member2\"]")
                        .required()
                ))
                .outputDescription("Returns { removedCount: n }")
                .build(),

            OperationDef.create("zrange", "Get Range")
                .description("Get members by rank range")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Sorted set key name")
                        .required(),
                    FieldDef.integer("start", "Start")
                        .withDescription("Start rank (0-based)")
                        .withDefault(0),
                    FieldDef.integer("stop", "Stop")
                        .withDescription("Stop rank (-1 for all)")
                        .withDefault(-1),
                    FieldDef.bool("withScores", "With Scores")
                        .withDescription("Include scores in result")
                        .withDefault(false),
                    FieldDef.bool("reverse", "Reverse")
                        .withDescription("Return in reverse order (highest first)")
                        .withDefault(false)
                ))
                .outputDescription("Returns { members: [...] }")
                .build(),

            OperationDef.create("zscore", "Get Score")
                .description("Get the score of a member")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Sorted set key name")
                        .required(),
                    FieldDef.string("member", "Member")
                        .withDescription("Member name")
                        .required()
                ))
                .outputDescription("Returns { score: n, exists: boolean }")
                .build(),

            OperationDef.create("zincrby", "Increment Score")
                .description("Increment the score of a member")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Sorted set key name")
                        .required(),
                    FieldDef.string("member", "Member")
                        .withDescription("Member name")
                        .required(),
                    FieldDef.number("amount", "Amount")
                        .withDescription("Amount to increment (can be negative)")
                        .withDefault(1.0)
                ))
                .outputDescription("Returns { score: n }")
                .build(),

            OperationDef.create("zcard", "Get Size")
                .description("Get the number of members")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Sorted set key name")
                        .required()
                ))
                .outputDescription("Returns { size: n }")
                .build()
        ));

        // Key operations
        operations.put("key", List.of(
            OperationDef.create("del", "Delete")
                .description("Delete one or more keys")
                .fields(List.of(
                    FieldDef.textarea("keys", "Keys")
                        .withDescription("Keys to delete as JSON array")
                        .withPlaceholder("[\"key1\", \"key2\"]")
                        .required()
                ))
                .outputDescription("Returns { deletedCount: n }")
                .build(),

            OperationDef.create("exists", "Exists")
                .description("Check if keys exist")
                .fields(List.of(
                    FieldDef.textarea("keys", "Keys")
                        .withDescription("Keys to check as JSON array")
                        .withPlaceholder("[\"key1\", \"key2\"]")
                        .required()
                ))
                .outputDescription("Returns { existsCount: n }")
                .build(),

            OperationDef.create("expire", "Set Expiration")
                .description("Set a timeout on a key")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Key name")
                        .required(),
                    FieldDef.integer("seconds", "Seconds")
                        .withDescription("Timeout in seconds")
                        .required()
                ))
                .outputDescription("Returns { success: boolean }")
                .build(),

            OperationDef.create("ttl", "Get TTL")
                .description("Get the remaining time to live of a key")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Key name")
                        .required()
                ))
                .outputDescription("Returns { ttl: n } (-1 = no expiry, -2 = not found)")
                .build(),

            OperationDef.create("keys", "Find Keys")
                .description("Find keys matching a pattern")
                .fields(List.of(
                    FieldDef.string("pattern", "Pattern")
                        .withDescription("Pattern to match (use * as wildcard)")
                        .withPlaceholder("user:*")
                        .required(),
                    FieldDef.integer("limit", "Limit")
                        .withDescription("Maximum keys to return")
                        .withDefault(100)
                ))
                .outputDescription("Returns { keys: [...], count: n }")
                .build(),

            OperationDef.create("type", "Get Type")
                .description("Get the type of a key")
                .fields(List.of(
                    FieldDef.string("key", "Key")
                        .withDescription("Key name")
                        .required()
                ))
                .outputDescription("Returns { type: \"string\"|\"list\"|\"set\"|\"zset\"|\"hash\"|\"none\" }")
                .build()
        ));

        // Pub/Sub operations
        operations.put("pubsub", List.of(
            OperationDef.create("publish", "Publish")
                .description("Publish a message to a channel")
                .fields(List.of(
                    FieldDef.string("channel", "Channel")
                        .withDescription("Channel name")
                        .required(),
                    FieldDef.textarea("message", "Message")
                        .withDescription("Message to publish")
                        .required()
                ))
                .outputDescription("Returns { subscriberCount: n }")
                .build()
        ));

        return operations;
    }

    @Override
    public NodeExecutionResult executeOperation(
        NodeExecutionContext context,
        String resource,
        String operation,
        Map<String, Object> credential,
        Map<String, Object> params
    ) {
        try {
            RedisCommands<String, String> commands = getCommands(credential);

            return switch (resource) {
                case "string" -> RedisStringOperations.execute(commands, operation, params, objectMapper);
                case "hash" -> RedisHashOperations.execute(commands, operation, params, objectMapper);
                case "list" -> RedisListOperations.execute(commands, operation, params, objectMapper);
                case "set" -> RedisSetOperations.execute(commands, operation, params, objectMapper);
                case "sortedSet" -> RedisSortedSetOperations.execute(commands, operation, params, objectMapper);
                case "key" -> RedisKeyOperations.execute(commands, operation, params, objectMapper);
                case "pubsub" -> RedisPubSubOperations.execute(commands, operation, params, objectMapper);
                default -> NodeExecutionResult.failure("Unknown resource: " + resource);
            };
        } catch (Exception e) {
            log.error("Redis operation failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("Redis error: " + e.getMessage());
        }
    }

    // ==================== Connection Management ====================

    private RedisCommands<String, String> getCommands(Map<String, Object> credential) {
        String cacheKey = generateCacheKey(credential);

        ClientEntry entry = clients.compute(cacheKey, (key, existing) -> {
            if (existing != null && System.currentTimeMillis() - existing.createdAt < CLIENT_TTL_MS) {
                return existing;
            }
            if (existing != null) {
                try {
                    existing.connection.close();
                    existing.client.shutdown();
                } catch (Exception e) {
                    log.warn("Error closing Redis client: {}", e.getMessage());
                }
            }
            return createClient(credential);
        });

        return entry.connection.sync();
    }

    private ClientEntry createClient(Map<String, Object> credential) {
        String url = getCredentialValue(credential, "url");

        RedisURI uri;
        if (url != null && !url.isEmpty()) {
            uri = RedisURI.create(url);
        } else {
            String host = getCredentialValue(credential, "host");
            String portStr = getCredentialValue(credential, "port");
            String password = getCredentialValue(credential, "password");
            String databaseStr = getCredentialValue(credential, "database");
            String sslStr = getCredentialValue(credential, "ssl");

            if (host == null || host.isEmpty()) {
                host = "localhost";
            }
            int port = 6379;
            if (portStr != null && !portStr.isEmpty()) {
                try {
                    port = Integer.parseInt(portStr);
                } catch (NumberFormatException e) {
                    // Use default
                }
            }
            // Default to database 1 to avoid conflict with application cache (database 0)
            int database = 1;
            if (databaseStr != null && !databaseStr.isEmpty()) {
                try {
                    database = Integer.parseInt(databaseStr);
                } catch (NumberFormatException e) {
                    // Use default (1)
                }
            }

            RedisURI.Builder builder = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withDatabase(database)
                .withTimeout(Duration.ofSeconds(10));

            if (password != null && !password.isEmpty()) {
                builder.withPassword(password.toCharArray());
            }
            if ("true".equalsIgnoreCase(sslStr)) {
                builder.withSsl(true);
            }

            uri = builder.build();
        }

        RedisClient client = RedisClient.create(uri);
        StatefulRedisConnection<String, String> connection = client.connect();

        log.info("Created Redis client for {}:{}", uri.getHost(), uri.getPort());

        return new ClientEntry(client, connection, System.currentTimeMillis());
    }

    private String generateCacheKey(Map<String, Object> credential) {
        String url = getCredentialValue(credential, "url");
        if (url != null && !url.isEmpty()) {
            return String.valueOf(url.hashCode());
        }
        String host = getCredentialValue(credential, "host");
        String port = getCredentialValue(credential, "port");
        String database = getCredentialValue(credential, "database");
        return String.format("%s:%s:%s", host, port, database);
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "object")
            )
        );
    }

    // Client cache entry
    private static class ClientEntry {
        final RedisClient client;
        final StatefulRedisConnection<String, String> connection;
        final long createdAt;

        ClientEntry(RedisClient client, StatefulRedisConnection<String, String> connection, long createdAt) {
            this.client = client;
            this.connection = connection;
            this.createdAt = createdAt;
        }
    }
}
