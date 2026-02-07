package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.ai.module.SimpleAIProviderRegistry;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.handlers.scripting.JavaScriptEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class AiTransformNodeHandlerTest {

    @Mock
    private SimpleAIProviderRegistry aiProviderRegistry;

    @Mock
    private JavaScriptEngine javaScriptEngine;

    private ObjectMapper objectMapper;

    private AiTransformNodeHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new AiTransformNodeHandler(aiProviderRegistry, javaScriptEngine, objectMapper);
    }

    // ==================== Basic Properties ====================

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType_returnsAiTransform() {
            assertThat(handler.getType()).isEqualTo("aiTransform");
        }

        @Test
        void getDisplayName_containsAI() {
            assertThat(handler.getDisplayName()).contains("AI");
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
        void getIcon_returnsRobot() {
            assertThat(handler.getIcon()).isEqualTo("robot");
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
        void getConfigSchema_containsTransformDescriptionProperty() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("transformDescription");
        }

        @Test
        void getConfigSchema_containsCacheCodeProperty() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("cacheCode");
        }

        @Test
        void getConfigSchema_containsTimeoutProperty() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("timeout");
        }

        @Test
        void getConfigSchema_hasRequiredField() {
            Map<String, Object> schema = handler.getConfigSchema();
            assertThat(schema).containsKey("required");
            @SuppressWarnings("unchecked")
            List<String> required = (List<String>) schema.get("required");
            assertThat(required).contains("transformDescription");
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

        @Test
        void getInterfaceDefinition_hasInputPort() {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> inputs = (List<Map<String, Object>>) handler.getInterfaceDefinition().get("inputs");
            assertThat(inputs).isNotEmpty();
        }

        @Test
        void getInterfaceDefinition_hasOutputPort() {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> outputs = (List<Map<String, Object>>) handler.getInterfaceDefinition().get("outputs");
            assertThat(outputs).isNotEmpty();
        }
    }

    // ==================== Execution Validation ====================

    @Nested
    @DisplayName("Execution Validation")
    class ExecutionValidation {

        @Test
        void execute_missingTransformDescription_returnFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("transformDescription", "");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("node-1")
                    .nodeType("aiTransform")
                    .nodeConfig(config)
                    .inputData(new HashMap<>())
                    .userId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);
            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void execute_blankTransformDescription_returnFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("transformDescription", "   ");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("node-1")
                    .nodeType("aiTransform")
                    .nodeConfig(config)
                    .inputData(new HashMap<>())
                    .userId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);
            assertThat(result.isSuccess()).isFalse();
        }
    }
}
