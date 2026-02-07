package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class OutputNodeHandlerTest {

    private OutputNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OutputNodeHandler();
    }

    @Test
    void getType_returnsOutput() {
        assertThat(handler.getType()).isEqualTo("output");
    }

    @Test
    void getCategory_returnsFlowControl() {
        assertThat(handler.getCategory()).isEqualTo("Flow Control");
    }

    @Test
    void execute_allMode_passesAllInputData() {
        Map<String, Object> input = Map.of("key1", "val1", "key2", 42);
        NodeExecutionContext context = buildContextWithInput(Map.of("outputMode", "all"), input);

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("key1", "val1");
        assertThat(result.getOutput()).containsEntry("key2", 42);
    }

    @Test
    void execute_defaultMode_actsAsAll() {
        Map<String, Object> input = Map.of("data", "test");
        NodeExecutionContext context = buildContextWithInput(Map.of(), input);

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("data", "test");
    }

    @Test
    void execute_selectedMode_onlyOutputsSelectedFields() {
        Map<String, Object> input = Map.of("name", "John", "age", 30, "email", "john@test.com");
        Map<String, Object> config = new HashMap<>();
        config.put("outputMode", "selected");
        config.put("selectedFields", List.of("name", "email"));
        NodeExecutionContext context = buildContextWithInput(config, input);

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("name", "John");
        assertThat(result.getOutput()).containsEntry("email", "john@test.com");
        assertThat(result.getOutput()).doesNotContainKey("age");
    }

    @Test
    void execute_selectedMode_nonExistingField_skipsGracefully() {
        Map<String, Object> input = Map.of("name", "John");
        Map<String, Object> config = new HashMap<>();
        config.put("outputMode", "selected");
        config.put("selectedFields", List.of("name", "nonExisting"));
        NodeExecutionContext context = buildContextWithInput(config, input);

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("name", "John");
        assertThat(result.getOutput()).hasSize(1);
    }

    @Test
    void execute_includeMetadata_addsExecutionMetadata() {
        Map<String, Object> input = Map.of("data", "test");
        Map<String, Object> config = new HashMap<>();
        config.put("includeMetadata", true);
        NodeExecutionContext context = buildContextWithInput(config, input);

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsKey("_metadata");
        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) result.getOutput().get("_metadata");
        assertThat(metadata).containsKey("executionId");
        assertThat(metadata).containsKey("nodeId");
        assertThat(metadata).containsKey("timestamp");
    }

    @Test
    void execute_includeMetadataFalse_noMetadata() {
        Map<String, Object> input = Map.of("data", "test");
        NodeExecutionContext context = buildContextWithInput(Map.of("includeMetadata", false), input);

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).doesNotContainKey("_metadata");
    }

    @Test
    void execute_nullInput_returnsEmptyMap() {
        NodeExecutionContext context = NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("output-1")
            .nodeType("output")
            .nodeConfig(new HashMap<>(Map.of()))
            .inputData(null)
            .userId(UUID.randomUUID())
            .flowId(UUID.randomUUID())
            .build();

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).isNotNull();
    }

    @Test
    void execute_expressionMode_emptyExpression_returnsInput() {
        Map<String, Object> input = Map.of("data", "test");
        Map<String, Object> config = new HashMap<>();
        config.put("outputMode", "expression");
        config.put("outputExpression", "");
        NodeExecutionContext context = buildContextWithInput(config, input);

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("data", "test");
    }

    private NodeExecutionContext buildContextWithInput(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("output-1")
            .nodeType("output")
            .nodeConfig(new HashMap<>(config))
            .inputData(inputData)
            .userId(UUID.randomUUID())
            .flowId(UUID.randomUUID())
            .build();
    }
}
