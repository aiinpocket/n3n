package com.aiinpocket.n3n.execution.handler.handlers.ai.vector;

import com.aiinpocket.n3n.ai.provider.AiProviderFactory;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AiVectorSearchNodeHandlerTest {

    @Mock
    private AiProviderFactory providerFactory;

    @Mock
    private VectorStore vectorStore;

    private AiVectorSearchNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AiVectorSearchNodeHandler(providerFactory, vectorStore);
    }

    // ==================== Basic Properties ====================

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType_returnsAiVectorSearch() {
            assertThat(handler.getType()).isEqualTo("aiVectorSearch");
        }

        @Test
        void getDisplayName_returnsVectorSearch() {
            assertThat(handler.getDisplayName()).isEqualTo("Vector Search");
        }

        @Test
        void getCategory_returnsAI() {
            assertThat(handler.getCategory()).isEqualTo("AI");
        }

        @Test
        void getDescription_isNotBlank() {
            assertThat(handler.getDescription()).isNotBlank();
        }

        @Test
        void getIcon_returnsSearch() {
            assertThat(handler.getIcon()).isEqualTo("search");
        }

        @Test
        void supportsStreaming_returnsFalse() {
            assertThat(handler.supportsStreaming()).isFalse();
        }

        @Test
        void supportsAsync_returnsTrue() {
            assertThat(handler.supportsAsync()).isTrue();
        }
    }

    // ==================== Config Schema ====================

    @Nested
    @DisplayName("Config Schema")
    class ConfigSchemaTests {

        @Test
        void getConfigSchema_containsProperties() {
            Map<String, Object> schema = handler.getConfigSchema();
            assertThat(schema).containsKey("properties");
        }

        @Test
        void getConfigSchema_containsResourceAndOperation() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("resource");
            assertThat(properties).containsKey("operation");
        }

        @Test
        void getConfigSchema_containsNamespaceField() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("namespace");
        }
    }

    // ==================== Interface Definition ====================

    @Nested
    @DisplayName("Interface Definition")
    class InterfaceDefinitionTests {

        @Test
        void getInterfaceDefinition_containsInputsAndOutputs() {
            Map<String, Object> iface = handler.getInterfaceDefinition();
            assertThat(iface).containsKey("inputs");
            assertThat(iface).containsKey("outputs");
        }
    }

    // ==================== Resources & Operations ====================

    @Nested
    @DisplayName("Resources and Operations")
    class ResourcesAndOperations {

        @Test
        void getResources_containsVectorResource() {
            assertThat(handler.getResources()).containsKey("vector");
        }

        @Test
        void getOperations_containsVectorOperations() {
            assertThat(handler.getOperations()).containsKey("vector");
            assertThat(handler.getOperations().get("vector")).isNotEmpty();
        }

        @Test
        void getOperations_hasUpsertAndSearchOperations() {
            var ops = handler.getOperations().get("vector");
            assertThat(ops).anyMatch(op -> "upsert".equals(op.getName()));
            assertThat(ops).anyMatch(op -> "search".equals(op.getName()));
        }
    }

    // ==================== Execution Validation ====================

    @Nested
    @DisplayName("Execution Validation")
    class ExecutionValidation {

        @Test
        void execute_missingResource_returnFailure() {
            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("node-1")
                    .nodeType("aiVectorSearch")
                    .nodeConfig(new HashMap<>())
                    .inputData(new HashMap<>())
                    .userId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void execute_missingOperation_returnFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "vector");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("node-1")
                    .nodeType("aiVectorSearch")
                    .nodeConfig(config)
                    .inputData(new HashMap<>())
                    .userId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void execute_unknownResource_returnFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "nonexistent");
            config.put("operation", "upsert");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("node-1")
                    .nodeType("aiVectorSearch")
                    .nodeConfig(config)
                    .inputData(new HashMap<>())
                    .userId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);
            assertThat(result.isSuccess()).isFalse();
        }
    }
}
