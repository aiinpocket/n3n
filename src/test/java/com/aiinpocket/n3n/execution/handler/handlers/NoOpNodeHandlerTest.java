package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class NoOpNodeHandlerTest {

    private NoOpNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new NoOpNodeHandler();
    }

    @Test
    void getType_returnsNoOp() {
        assertThat(handler.getType()).isEqualTo("noOp");
    }

    @Test
    void getCategory_returnsFlowControl() {
        assertThat(handler.getCategory()).isEqualTo("Flow Control");
    }

    @Test
    void getDisplayName_returnsNoOperation() {
        assertThat(handler.getDisplayName()).isEqualTo("No Operation");
    }

    @Test
    void execute_withInputData_passesThrough() {
        Map<String, Object> input = Map.of("key1", "value1", "count", 42);
        NodeExecutionContext context = buildContextWithInput(Map.of(), input);

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("key1", "value1");
        assertThat(result.getOutput()).containsEntry("count", 42);
    }

    @Test
    void execute_withNullInput_returnsEmptyMap() {
        NodeExecutionContext context = NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("noop-1")
            .nodeType("noOp")
            .nodeConfig(Map.of())
            .inputData(null)
            .userId(UUID.randomUUID())
            .flowId(UUID.randomUUID())
            .build();

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).isEmpty();
    }

    @Test
    void execute_withLogDataEnabled_succeeds() {
        Map<String, Object> input = Map.of("data", "test");
        NodeExecutionContext context = buildContextWithInput(Map.of("logData", true), input);

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("data", "test");
    }

    @Test
    void execute_emptyInput_returnsEmptyMap() {
        NodeExecutionContext context = buildContextWithInput(Map.of(), Map.of());

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).isEmpty();
    }

    private NodeExecutionContext buildContextWithInput(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("noop-1")
            .nodeType("noOp")
            .nodeConfig(new HashMap<>(config))
            .inputData(inputData)
            .userId(UUID.randomUUID())
            .flowId(UUID.randomUUID())
            .build();
    }
}
