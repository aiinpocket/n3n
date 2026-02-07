package com.aiinpocket.n3n.execution.handler.handlers.database;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.multiop.OperationDef;
import com.aiinpocket.n3n.execution.handler.multiop.ResourceDef;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatabaseNodeHandlerTest {

    @Mock
    private DatabaseConnectionManager connectionManager;

    @Mock
    private CloudSqlConnectionFactory cloudSqlConnectionFactory;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DatabaseNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DatabaseNodeHandler(connectionManager, cloudSqlConnectionFactory, objectMapper);
    }

    // ==================== Basic Properties ====================

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType_returnsDatabase() {
            assertThat(handler.getType()).isEqualTo("database");
        }

        @Test
        void getDisplayName_returnsDatabase() {
            assertThat(handler.getDisplayName()).isEqualTo("Database");
        }

        @Test
        void getDescription_isNotBlank() {
            assertThat(handler.getDescription()).isNotBlank();
        }

        @Test
        void getDescription_mentionsMultipleDatabases() {
            assertThat(handler.getDescription()).contains("PostgreSQL", "MySQL");
        }

        @Test
        void getCategory_returnsData() {
            assertThat(handler.getCategory()).isEqualTo("Data");
        }

        @Test
        void getIcon_returnsDatabase() {
            assertThat(handler.getIcon()).isEqualTo("database");
        }

        @Test
        void getCredentialType_returnsDatabase() {
            assertThat(handler.getCredentialType()).isEqualTo("database");
        }

        @Test
        void supportsAsync_returnsTrue() {
            assertThat(handler.supportsAsync()).isTrue();
        }
    }

    // ==================== Resources ====================

    @Nested
    @DisplayName("Resources")
    class Resources {

        @Test
        void getResources_containsQueryExecuteRaw() {
            Map<String, ResourceDef> resources = handler.getResources();
            assertThat(resources).containsKeys("query", "execute", "raw");
        }

        @Test
        void getResources_hasCorrectDisplayNames() {
            Map<String, ResourceDef> resources = handler.getResources();
            assertThat(resources.get("query").getDisplayName()).isEqualTo("Query");
            assertThat(resources.get("execute").getDisplayName()).isEqualTo("Execute");
            assertThat(resources.get("raw").getDisplayName()).isEqualTo("Raw SQL");
        }
    }

    // ==================== Operations ====================

    @Nested
    @DisplayName("Operations")
    class Operations {

        @Test
        void getOperations_queryHasSelectSelectOneCount() {
            List<OperationDef> queryOps = handler.getOperations().get("query");
            List<String> opNames = queryOps.stream().map(OperationDef::getName).toList();
            assertThat(opNames).contains("select", "selectOne", "count");
        }

        @Test
        void getOperations_executeHasInsertUpdateDelete() {
            List<OperationDef> execOps = handler.getOperations().get("execute");
            List<String> opNames = execOps.stream().map(OperationDef::getName).toList();
            assertThat(opNames).contains("insert", "insertMany", "update", "delete");
        }

        @Test
        void getOperations_rawHasExecuteAndBatch() {
            List<OperationDef> rawOps = handler.getOperations().get("raw");
            List<String> opNames = rawOps.stream().map(OperationDef::getName).toList();
            assertThat(opNames).contains("execute", "batch");
        }

        @Test
        void getOperations_selectHasSqlField() {
            OperationDef select = handler.getOperations().get("query").stream()
                    .filter(op -> op.getName().equals("select")).findFirst().orElseThrow();
            boolean hasSql = select.getFields().stream().anyMatch(f -> f.getName().equals("sql"));
            assertThat(hasSql).isTrue();
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
            assertThat(schema.get("x-credential-type")).isEqualTo("database");
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
            config.put("operation", "select");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("resource");
        }

        @Test
        void execute_missingOperation_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "query");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("operation");
        }

        @Test
        void execute_unknownResource_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "nonexistent");
            config.put("operation", "select");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Unknown resource");
        }

        @Test
        void execute_unknownOperation_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "query");
            config.put("operation", "nonexistent");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Unknown operation");
        }
    }

    // ==================== Execution - Connection Failure ====================

    @Nested
    @DisplayName("Execution - Connection Failure")
    class ExecutionConnectionFailure {

        @Test
        void execute_connectionFails_returnsFailure() throws Exception {
            lenient().when(connectionManager.getConnection(
                    anyString(), anyString(), anyInt(), anyString(), anyString(), anyString(), any()))
                    .thenThrow(new SQLException("Connection refused"));

            Map<String, Object> config = new HashMap<>();
            config.put("resource", "query");
            config.put("operation", "select");
            config.put("credentialId", UUID.randomUUID().toString());
            config.put("sql", "SELECT 1");

            // The handler resolves credentials via context. Since no resolver is set,
            // it will use empty credential map, which defaults to postgresql.
            // The connectionManager mock may or may not be invoked depending on
            // credential resolution path, so we use lenient stubbing.
            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
        }
    }

    // ==================== Helper ====================

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("db-1")
                .nodeType("database")
                .nodeConfig(new HashMap<>(config))
                .inputData(new HashMap<>())
                .previousOutputs(new HashMap<>())
                .globalContext(new HashMap<>())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
