package com.aiinpocket.n3n.execution.handler.handlers.nosql;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class RedisNodeHandlerTest {

    private RedisNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RedisNodeHandler(new ObjectMapper());
    }

    // ==================== Basic Properties ====================

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType_returnsRedis() {
            assertThat(handler.getType()).isEqualTo("redis");
        }

        @Test
        void getDisplayName_returnsRedis() {
            assertThat(handler.getDisplayName()).isEqualTo("Redis");
        }

        @Test
        void getDescription_isNotBlank() {
            assertThat(handler.getDescription()).isNotBlank();
        }

        @Test
        void getDescription_mentionsRedis() {
            assertThat(handler.getDescription()).contains("Redis");
        }

        @Test
        void getCategory_returnsData() {
            assertThat(handler.getCategory()).isEqualTo("Data");
        }

        @Test
        void getIcon_returnsRedis() {
            assertThat(handler.getIcon()).isEqualTo("redis");
        }

        @Test
        void getCredentialType_returnsRedis() {
            assertThat(handler.getCredentialType()).isEqualTo("redis");
        }

        @Test
        void supportsAsync_returnsFalse() {
            assertThat(handler.supportsAsync()).isFalse();
        }
    }

    // ==================== Resources ====================

    @Nested
    @DisplayName("Resources")
    class Resources {

        @Test
        void getResources_containsStringHashListSetSortedSetKeyPubsub() {
            Map<String, ResourceDef> resources = handler.getResources();
            assertThat(resources).containsKeys("string", "hash", "list", "set", "sortedSet", "key", "pubsub");
        }

        @Test
        void getResources_hasSevenResources() {
            assertThat(handler.getResources()).hasSize(7);
        }

        @Test
        void getResources_stringHasDisplayName() {
            assertThat(handler.getResources().get("string").getDisplayName()).isEqualTo("String");
        }

        @Test
        void getResources_hashHasDisplayName() {
            assertThat(handler.getResources().get("hash").getDisplayName()).isEqualTo("Hash");
        }

        @Test
        void getResources_pubsubHasDisplayName() {
            assertThat(handler.getResources().get("pubsub").getDisplayName()).isEqualTo("Pub/Sub");
        }
    }

    // ==================== Operations ====================

    @Nested
    @DisplayName("Operations")
    class Operations {

        @Test
        void getOperations_stringHasGetSet() {
            List<OperationDef> stringOps = handler.getOperations().get("string");
            List<String> names = stringOps.stream().map(OperationDef::getName).toList();
            assertThat(names).contains("get", "set");
        }

        @Test
        void getOperations_hashHasHgetHset() {
            List<OperationDef> hashOps = handler.getOperations().get("hash");
            List<String> names = hashOps.stream().map(OperationDef::getName).toList();
            assertThat(names).contains("hget", "hset", "hgetall");
        }

        @Test
        void getOperations_listHasPushPop() {
            List<OperationDef> listOps = handler.getOperations().get("list");
            List<String> names = listOps.stream().map(OperationDef::getName).toList();
            assertThat(names).contains("lpush", "rpush", "lpop", "rpop");
        }

        @Test
        void getOperations_setHsSaddSmembers() {
            List<OperationDef> setOps = handler.getOperations().get("set");
            List<String> names = setOps.stream().map(OperationDef::getName).toList();
            assertThat(names).contains("sadd", "smembers");
        }

        @Test
        void getOperations_sortedSetHasZaddZrange() {
            List<OperationDef> sortedSetOps = handler.getOperations().get("sortedSet");
            List<String> names = sortedSetOps.stream().map(OperationDef::getName).toList();
            assertThat(names).contains("zadd", "zrange");
        }

        @Test
        void getOperations_keyHasDelExists() {
            List<OperationDef> keyOps = handler.getOperations().get("key");
            List<String> names = keyOps.stream().map(OperationDef::getName).toList();
            assertThat(names).contains("del", "exists");
        }

        @Test
        void getOperations_getHasKeyField() {
            OperationDef get = handler.getOperations().get("string").stream()
                    .filter(op -> op.getName().equals("get")).findFirst().orElseThrow();
            boolean hasKey = get.getFields().stream().anyMatch(f -> f.getName().equals("key"));
            assertThat(hasKey).isTrue();
        }
    }

    // ==================== Config Schema ====================

    @Nested
    @DisplayName("Config Schema")
    class ConfigSchema {

        @Test
        void getConfigSchema_containsProperties() {
            Map<String, Object> schema = handler.getConfigSchema();
            assertThat(schema).containsKey("properties");
        }

        @Test
        void getConfigSchema_isMultiOperation() {
            Map<String, Object> schema = handler.getConfigSchema();
            assertThat(schema.get("x-multi-operation")).isEqualTo(true);
        }

        @Test
        void getConfigSchema_hasCredentialType() {
            Map<String, Object> schema = handler.getConfigSchema();
            assertThat(schema.get("x-credential-type")).isEqualTo("redis");
        }

        @Test
        void getConfigSchema_hasResourceAndOperationProperties() {
            Map<String, Object> schema = handler.getConfigSchema();
            @SuppressWarnings("unchecked")
            Map<String, Object> props = (Map<String, Object>) schema.get("properties");
            assertThat(props).containsKeys("resource", "operation");
        }
    }

    // ==================== Interface Definition ====================

    @Nested
    @DisplayName("Interface Definition")
    class InterfaceDef {

        @Test
        void getInterfaceDefinition_containsInputsAndOutputs() {
            Map<String, Object> iface = handler.getInterfaceDefinition();
            assertThat(iface).containsKey("inputs").containsKey("outputs");
        }

        @Test
        @SuppressWarnings("unchecked")
        void getInterfaceDefinition_hasOutputOfTypeObject() {
            Map<String, Object> iface = handler.getInterfaceDefinition();
            List<Map<String, Object>> outputs = (List<Map<String, Object>>) iface.get("outputs");
            assertThat(outputs).isNotEmpty();
            assertThat(outputs.get(0).get("type")).isEqualTo("object");
        }
    }

    // ==================== Execution - Missing Config ====================

    @Nested
    @DisplayName("Execution - Missing Config")
    class ExecutionMissingConfig {

        @Test
        void execute_missingResource_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "get");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("resource");
        }

        @Test
        void execute_missingOperation_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "string");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("operation");
        }

        @Test
        void execute_unknownResource_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "nonexistent");
            config.put("operation", "get");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Unknown resource");
        }

        @Test
        void execute_unknownOperation_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "string");
            config.put("operation", "nonexistent");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Unknown operation");
        }

        @Test
        void execute_emptyConfig_returnsFailure() {
            NodeExecutionResult result = handler.execute(buildContext(new HashMap<>()));
            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ==================== Helper ====================

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("redis-1")
                .nodeType("redis")
                .nodeConfig(new HashMap<>(config))
                .inputData(new HashMap<>())
                .previousOutputs(new HashMap<>())
                .globalContext(new HashMap<>())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
