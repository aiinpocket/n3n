package com.aiinpocket.n3n.execution.handler.handlers.ai.memory;

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
class AiMemoryNodeHandlerTest {

    @Mock
    private AiProviderFactory providerFactory;

    @Mock
    private MemoryStore memoryStore;

    private AiMemoryNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AiMemoryNodeHandler(providerFactory, memoryStore);
    }

    // ==================== Basic Properties ====================

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType_returnsAiMemory() {
            assertThat(handler.getType()).isEqualTo("aiMemory");
        }

        @Test
        void getDisplayName_returnsAIMemory() {
            assertThat(handler.getDisplayName()).isEqualTo("AI Memory");
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
        void getIcon_returnsDatabase() {
            assertThat(handler.getIcon()).isEqualTo("database");
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
        void getConfigSchema_containsSessionIdField() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("sessionId");
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
        void getResources_containsMemoryResource() {
            assertThat(handler.getResources()).containsKey("memory");
        }

        @Test
        void getOperations_containsMemoryOperations() {
            assertThat(handler.getOperations()).containsKey("memory");
            assertThat(handler.getOperations().get("memory")).isNotEmpty();
        }

        @Test
        void getOperations_hasStoreAndGetHistoryAndClear() {
            var ops = handler.getOperations().get("memory");
            assertThat(ops).anyMatch(op -> "store".equals(op.getName()));
            assertThat(ops).anyMatch(op -> "getHistory".equals(op.getName()));
            assertThat(ops).anyMatch(op -> "clear".equals(op.getName()));
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
                    .nodeType("aiMemory")
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
            config.put("resource", "memory");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("node-1")
                    .nodeType("aiMemory")
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
            config.put("operation", "store");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("node-1")
                    .nodeType("aiMemory")
                    .nodeConfig(config)
                    .inputData(new HashMap<>())
                    .userId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);
            assertThat(result.isSuccess()).isFalse();
        }
    }
}
