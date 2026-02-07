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

class MongoDBNodeHandlerTest {

    private MongoDBNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MongoDBNodeHandler(new ObjectMapper());
    }

    // ==================== Basic Properties ====================

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType_returnsMongodb() {
            assertThat(handler.getType()).isEqualTo("mongodb");
        }

        @Test
        void getDisplayName_returnsMongoDB() {
            assertThat(handler.getDisplayName()).isEqualTo("MongoDB");
        }

        @Test
        void getDescription_isNotBlank() {
            assertThat(handler.getDescription()).isNotBlank();
        }

        @Test
        void getDescription_mentionsMongoDB() {
            assertThat(handler.getDescription()).contains("MongoDB");
        }

        @Test
        void getCategory_returnsData() {
            assertThat(handler.getCategory()).isEqualTo("Data");
        }

        @Test
        void getIcon_returnsMongodb() {
            assertThat(handler.getIcon()).isEqualTo("mongodb");
        }

        @Test
        void getCredentialType_returnsMongodb() {
            assertThat(handler.getCredentialType()).isEqualTo("mongodb");
        }

        @Test
        void supportsAsync_returnsFalse() {
            // MultiOperationNodeHandler does not override supportsAsync by default
            assertThat(handler.supportsAsync()).isFalse();
        }
    }

    // ==================== Resources ====================

    @Nested
    @DisplayName("Resources")
    class Resources {

        @Test
        void getResources_containsDocumentAggregateCollection() {
            Map<String, ResourceDef> resources = handler.getResources();
            assertThat(resources).containsKeys("document", "aggregate", "collection");
        }

        @Test
        void getResources_hasThreeResources() {
            assertThat(handler.getResources()).hasSize(3);
        }

        @Test
        void getResources_documentHasDisplayName() {
            assertThat(handler.getResources().get("document").getDisplayName()).isEqualTo("Document");
        }

        @Test
        void getResources_aggregateHasDisplayName() {
            assertThat(handler.getResources().get("aggregate").getDisplayName()).isEqualTo("Aggregate");
        }

        @Test
        void getResources_collectionHasDisplayName() {
            assertThat(handler.getResources().get("collection").getDisplayName()).isEqualTo("Collection");
        }
    }

    // ==================== Operations ====================

    @Nested
    @DisplayName("Operations")
    class Operations {

        @Test
        void getOperations_documentHasCrudOperations() {
            List<OperationDef> docOps = handler.getOperations().get("document");
            List<String> names = docOps.stream().map(OperationDef::getName).toList();
            assertThat(names).contains("find", "findOne", "insertOne", "insertMany",
                    "updateOne", "updateMany", "deleteOne", "deleteMany", "count", "distinct");
        }

        @Test
        void getOperations_aggregateHasPipeline() {
            List<OperationDef> aggOps = handler.getOperations().get("aggregate");
            List<String> names = aggOps.stream().map(OperationDef::getName).toList();
            assertThat(names).contains("pipeline");
        }

        @Test
        void getOperations_collectionHasManagementOps() {
            List<OperationDef> colOps = handler.getOperations().get("collection");
            List<String> names = colOps.stream().map(OperationDef::getName).toList();
            assertThat(names).contains("list", "create", "drop", "createIndex", "listIndexes", "dropIndex");
        }

        @Test
        void getOperations_findHasCollectionField() {
            OperationDef find = handler.getOperations().get("document").stream()
                    .filter(op -> op.getName().equals("find")).findFirst().orElseThrow();
            boolean hasCollection = find.getFields().stream().anyMatch(f -> f.getName().equals("collection"));
            assertThat(hasCollection).isTrue();
        }

        @Test
        void getOperations_insertOneRequiresDocument() {
            OperationDef insertOne = handler.getOperations().get("document").stream()
                    .filter(op -> op.getName().equals("insertOne")).findFirst().orElseThrow();
            boolean hasDocument = insertOne.getFields().stream()
                    .anyMatch(f -> f.getName().equals("document") && f.isRequired());
            assertThat(hasDocument).isTrue();
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
            assertThat(schema.get("x-credential-type")).isEqualTo("mongodb");
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
            config.put("operation", "find");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("resource");
        }

        @Test
        void execute_missingOperation_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "document");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).containsIgnoringCase("operation");
        }

        @Test
        void execute_unknownResource_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "nonexistent");
            config.put("operation", "find");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Unknown resource");
        }

        @Test
        void execute_unknownOperation_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "document");
            config.put("operation", "nonexistent");

            NodeExecutionResult result = handler.execute(buildContext(config));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Unknown operation");
        }
    }

    // ==================== Helper ====================

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("mongo-1")
                .nodeType("mongodb")
                .nodeConfig(new HashMap<>(config))
                .inputData(new HashMap<>())
                .previousOutputs(new HashMap<>())
                .globalContext(new HashMap<>())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
