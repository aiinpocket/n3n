package com.aiinpocket.n3n.execution.handler.handlers.ai.chat;

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
class AiChatNodeHandlerTest {

    @Mock
    private AiProviderFactory providerFactory;

    private AiChatNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new AiChatNodeHandler(providerFactory);
    }

    // ==================== Basic Properties ====================

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType_returnsAiChat() {
            assertThat(handler.getType()).isEqualTo("aiChat");
        }

        @Test
        void getDisplayName_containsAIChat() {
            assertThat(handler.getDisplayName()).isEqualTo("AI Chat");
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
        void getIcon_returnsMessage() {
            assertThat(handler.getIcon()).isEqualTo("message");
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
        void getConfigSchema_containsResourceProperty() {
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
        void getConfigSchema_containsPromptField() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("prompt");
        }

        @Test
        void getConfigSchema_containsModelField() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("model");
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
        void getResources_containsChatResource() {
            assertThat(handler.getResources()).containsKey("chat");
        }

        @Test
        void getOperations_containsChatOperations() {
            assertThat(handler.getOperations()).containsKey("chat");
            assertThat(handler.getOperations().get("chat")).isNotEmpty();
        }

        @Test
        void getOperations_hasSendMessageOperation() {
            var ops = handler.getOperations().get("chat");
            assertThat(ops).anyMatch(op -> "sendMessage".equals(op.getName()));
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
                    .nodeType("aiChat")
                    .nodeConfig(new HashMap<>())
                    .inputData(new HashMap<>())
                    .userId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Resource");
        }

        @Test
        void execute_missingOperation_returnFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "chat");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("node-1")
                    .nodeType("aiChat")
                    .nodeConfig(config)
                    .inputData(new HashMap<>())
                    .userId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Operation");
        }

        @Test
        void execute_unknownResource_returnFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "nonexistent");
            config.put("operation", "sendMessage");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("node-1")
                    .nodeType("aiChat")
                    .nodeConfig(config)
                    .inputData(new HashMap<>())
                    .userId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Unknown resource");
        }
    }
}
