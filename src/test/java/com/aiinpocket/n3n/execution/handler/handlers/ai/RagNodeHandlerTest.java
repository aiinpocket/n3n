package com.aiinpocket.n3n.execution.handler.handlers.ai;

import com.aiinpocket.n3n.ai.rag.RagService;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagNodeHandlerTest {

    @Mock
    private RagService ragService;

    private RagNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RagNodeHandler(ragService);
    }

    // ==================== Basic Properties ====================

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {

        @Test
        void getType_returnsAiRag() {
            assertThat(handler.getType()).isEqualTo("aiRag");
        }

        @Test
        void getDisplayName_returnsRagQA() {
            assertThat(handler.getDisplayName()).isEqualTo("RAG Q&A");
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
        void getConfigSchema_containsOperationProperty() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("operation");
        }

        @Test
        void getConfigSchema_containsStoreNameProperty() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("storeName");
        }

        @Test
        void getConfigSchema_containsTopKProperty() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("topK");
        }

        @Test
        void getConfigSchema_containsMinScoreProperty() {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) handler.getConfigSchema().get("properties");
            assertThat(properties).containsKey("minScore");
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
        void getInterfaceDefinition_hasMultipleInputPorts() {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> inputs = (List<Map<String, Object>>) handler.getInterfaceDefinition().get("inputs");
            assertThat(inputs).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        void getInterfaceDefinition_hasMultipleOutputPorts() {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> outputs = (List<Map<String, Object>>) handler.getInterfaceDefinition().get("outputs");
            assertThat(outputs).hasSizeGreaterThanOrEqualTo(2);
        }
    }

    // ==================== Execution ====================

    @Nested
    @DisplayName("Execution")
    class ExecutionTests {

        @Test
        void execute_qaOperation_withQuestion_returnsAnswer() {
            when(ragService.ask(anyString(), any())).thenReturn("This is the answer");

            Map<String, Object> config = new HashMap<>();
            config.put("operation", "qa");

            Map<String, Object> inputData = new HashMap<>();
            inputData.put("question", "What is N3N?");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("node-1")
                    .nodeType("aiRag")
                    .nodeConfig(config)
                    .inputData(inputData)
                    .userId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("answer");
            assertThat(result.getOutput().get("answer")).isEqualTo("This is the answer");
        }

        @Test
        void execute_qaOperation_missingQuestion_returnFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "qa");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("node-1")
                    .nodeType("aiRag")
                    .nodeConfig(config)
                    .inputData(new HashMap<>())
                    .userId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("required");
        }

        @Test
        void execute_unknownOperation_returnFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "nonexistent");

            NodeExecutionContext context = NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID())
                    .nodeId("node-1")
                    .nodeType("aiRag")
                    .nodeConfig(config)
                    .inputData(new HashMap<>())
                    .userId(UUID.randomUUID())
                    .build();

            NodeExecutionResult result = handler.execute(context);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("Unknown RAG operation");
        }
    }
}
