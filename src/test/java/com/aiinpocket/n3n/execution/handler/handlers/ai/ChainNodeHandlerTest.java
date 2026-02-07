package com.aiinpocket.n3n.execution.handler.handlers.ai;

import com.aiinpocket.n3n.ai.chain.executor.ChainExecutor;
import com.aiinpocket.n3n.ai.memory.MemoryManager;
import com.aiinpocket.n3n.ai.service.AiService;
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
class ChainNodeHandlerTest {

    @Mock
    private AiService aiService;

    @Mock
    private MemoryManager memoryManager;

    @Mock
    private ChainExecutor chainExecutor;

    private ChainNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ChainNodeHandler(aiService, memoryManager, chainExecutor);
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
        void getCategory_returnsAi() {
            assertThat(handler.getCategory()).isEqualTo("ai");
        }

        @Test
        void getDescription_isNotBlank() {
            assertThat(handler.getDescription()).isNotBlank();
        }

        @Test
        void getIcon_returnsDefault() {
            assertThat(handler.getIcon()).isEqualTo("default");
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
        void getConfigSchema_containsChainTypeProperty() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("chainType");
        }

        @Test
        void getConfigSchema_containsPromptTemplateProperty() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("promptTemplate");
        }

        @Test
        void getConfigSchema_containsModelProperty() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("model");
        }

        @Test
        void getConfigSchema_containsTimeoutProperty() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("timeout");
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
            assertThat(inputs.get(0)).containsKey("name");
        }

        @Test
        void getInterfaceDefinition_hasOutputPort() {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> outputs = (List<Map<String, Object>>) handler.getInterfaceDefinition().get("outputs");
            assertThat(outputs).isNotEmpty();
        }
    }

    // ==================== Validation ====================

    @Nested
    @DisplayName("Validation")
    class ValidationTests {

        @Test
        void validateConfig_withValidConfig_returnsValid() {
            Map<String, Object> config = new HashMap<>();
            config.put("chainType", "llm");
            config.put("promptTemplate", "{input}");

            var result = handler.validateConfig(config);
            assertThat(result.isValid()).isTrue();
        }

        @Test
        void validateConfig_withNullConfig_returnsValid() {
            // Default implementation returns valid
            var result = handler.validateConfig(null);
            assertThat(result.isValid()).isTrue();
        }
    }
}
