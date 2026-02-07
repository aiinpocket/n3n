package com.aiinpocket.n3n.execution.handler.handlers.ai.chain;

import com.aiinpocket.n3n.ai.provider.AiProviderFactory;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.handlers.ai.vector.VectorStore;
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
class AiChainNodeHandlerTest {

    @Mock
    private AiProviderFactory providerFactory;

    @Mock
    private VectorStore vectorStore;

    private AiChainNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AiChainNodeHandler(providerFactory, vectorStore);
    }

    // ==================== Basic Properties ====================

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType_returnsAiChain() {
            assertThat(handler.getType()).isEqualTo("aiChain");
        }

        @Test
        void getDisplayName_returnsAIChain() {
            assertThat(handler.getDisplayName()).isEqualTo("AI Chain");
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
        void getIcon_returnsLink() {
            assertThat(handler.getIcon()).isEqualTo("link");
        }

        @Test
        void supportsStreaming_returnsTrue() {
            assertThat(handler.supportsStreaming()).isTrue();
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
        void getConfigSchema_containsProviderField() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("provider");
        }

        @Test
        void getConfigSchema_containsQueryField() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("query");
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
        void getResources_containsChainResource() {
            assertThat(handler.getResources()).containsKey("chain");
        }

        @Test
        void getOperations_containsChainOperations() {
            assertThat(handler.getOperations()).containsKey("chain");
            assertThat(handler.getOperations().get("chain")).isNotEmpty();
        }

        @Test
        void getOperations_hasRagOperation() {
            var ops = handler.getOperations().get("chain");
            assertThat(ops).anyMatch(op -> "rag".equals(op.getName()));
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
                    .nodeType("aiChain")
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
            config.put("resource", "chain");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("node-1")
                    .nodeType("aiChain")
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
            config.put("operation", "rag");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("node-1")
                    .nodeType("aiChain")
                    .nodeConfig(config)
                    .inputData(new HashMap<>())
                    .userId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);
            assertThat(result.isSuccess()).isFalse();
        }
    }
}
