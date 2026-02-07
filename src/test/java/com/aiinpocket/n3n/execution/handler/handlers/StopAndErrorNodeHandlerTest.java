package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class StopAndErrorNodeHandlerTest {

    private StopAndErrorNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new StopAndErrorNodeHandler();
    }

    @Test
    void getType_returnsStopAndError() {
        assertThat(handler.getType()).isEqualTo("stopAndError");
    }

    @Test
    void getCategory_returnsFlowControl() {
        assertThat(handler.getCategory()).isEqualTo("Flow Control");
    }

    @Test
    void getDisplayName_returnsStopAndError() {
        assertThat(handler.getDisplayName()).isEqualTo("Stop And Error");
    }

    @Test
    void execute_defaultConfig_returnsFailureWithDefaults() {
        NodeExecutionContext context = buildContext(Map.of());

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("Workflow stopped");
        assertThat(result.getOutput()).containsEntry("code", "WORKFLOW_STOPPED");
        assertThat(result.getOutput()).containsEntry("message", "Workflow stopped");
        assertThat(result.getOutput()).containsKey("nodeId");
        assertThat(result.getOutput()).containsKey("timestamp");
    }

    @Test
    void execute_customMessage_returnsCustomError() {
        NodeExecutionContext context = buildContext(Map.of(
            "errorMessage", "Invalid data received",
            "errorCode", "INVALID_DATA"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("Invalid data received");
        assertThat(result.getOutput()).containsEntry("code", "INVALID_DATA");
        assertThat(result.getOutput()).containsEntry("message", "Invalid data received");
    }

    @Test
    void execute_includeInput_addsInputToErrorDetails() {
        Map<String, Object> input = Map.of("user", "john", "action", "delete");
        NodeExecutionContext context = buildContextWithInput(
            Map.of("errorMessage", "Forbidden", "includeInput", true),
            input
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getOutput()).containsKey("input");
        @SuppressWarnings("unchecked")
        Map<String, Object> inputInError = (Map<String, Object>) result.getOutput().get("input");
        assertThat(inputInError).containsEntry("user", "john");
    }

    @Test
    void execute_includeInputFalse_doesNotAddInput() {
        Map<String, Object> input = Map.of("sensitive", "data");
        NodeExecutionContext context = buildContextWithInput(
            Map.of("errorMessage", "Error", "includeInput", false),
            input
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getOutput()).doesNotContainKey("input");
    }

    @Test
    void execute_noInputData_includeInputTrue_doesNotFail() {
        NodeExecutionContext context = NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("stop-1")
            .nodeType("stopAndError")
            .nodeConfig(new HashMap<>(Map.of("includeInput", true)))
            .inputData(null)
            .userId(UUID.randomUUID())
            .flowId(UUID.randomUUID())
            .build();

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getOutput()).doesNotContainKey("input");
    }

    @Test
    void execute_outputContainsNodeId() {
        NodeExecutionContext context = buildContext(Map.of());

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.getOutput().get("nodeId")).isEqualTo("stop-1");
    }

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("stop-1")
            .nodeType("stopAndError")
            .nodeConfig(new HashMap<>(config))
            .inputData(Map.of())
            .userId(UUID.randomUUID())
            .flowId(UUID.randomUUID())
            .build();
    }

    private NodeExecutionContext buildContextWithInput(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("stop-1")
            .nodeType("stopAndError")
            .nodeConfig(new HashMap<>(config))
            .inputData(inputData)
            .userId(UUID.randomUUID())
            .flowId(UUID.randomUUID())
            .build();
    }
}
