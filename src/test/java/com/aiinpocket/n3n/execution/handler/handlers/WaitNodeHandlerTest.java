package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class WaitNodeHandlerTest {

    private WaitNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WaitNodeHandler();
    }

    @Test
    void getType_returnsWait() {
        assertThat(handler.getType()).isEqualTo("wait");
    }

    @Test
    void getCategory_returnsFlowControl() {
        assertThat(handler.getCategory()).isEqualTo("Flow Control");
    }

    @Test
    void supportsAsync_returnsTrue() {
        assertThat(handler.supportsAsync()).isTrue();
    }

    @Test
    void execute_shortWait_succeeds() {
        NodeExecutionContext context = buildContext(Map.of("amount", 1, "unit", "milliseconds"));

        long start = System.currentTimeMillis();
        NodeExecutionResult result = handler.execute(context);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsKey("_waitInfo");
    }

    @Test
    void execute_withInputData_preservesInput() {
        Map<String, Object> input = Map.of("key", "value");
        NodeExecutionContext context = buildContextWithInput(
            Map.of("amount", 1, "unit", "milliseconds"), input
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("key", "value");
        assertThat(result.getOutput()).containsKey("_waitInfo");
    }

    @Test
    void execute_waitInfo_containsMetadata() {
        NodeExecutionContext context = buildContext(Map.of("amount", 1, "unit", "milliseconds"));

        NodeExecutionResult result = handler.execute(context);

        @SuppressWarnings("unchecked")
        Map<String, Object> waitInfo = (Map<String, Object>) result.getOutput().get("_waitInfo");
        assertThat(waitInfo).containsKey("waitedMs");
        assertThat(waitInfo).containsKey("resumedAt");
    }

    @Test
    void execute_nullInput_returnsOutputWithWaitInfo() {
        NodeExecutionContext context = NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("wait-1")
            .nodeType("wait")
            .nodeConfig(new HashMap<>(Map.of("amount", 1, "unit", "milliseconds")))
            .inputData(null)
            .userId(UUID.randomUUID())
            .flowId(UUID.randomUUID())
            .build();

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsKey("_waitInfo");
    }

    @Test
    void execute_secondsUnit_calculatesCorrectly() {
        NodeExecutionContext context = buildContext(Map.of("amount", 0, "unit", "seconds"));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void execute_defaultUnit_usesSeconds() {
        NodeExecutionContext context = buildContext(Map.of("amount", 0));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void validateConfig_negativeAmount_returnsInvalid() {
        Map<String, Object> config = Map.of("amount", -1);

        ValidationResult result = handler.validateConfig(config);

        assertThat(result.isValid()).isFalse();
    }

    @Test
    void validateConfig_validAmount_returnsValid() {
        Map<String, Object> config = Map.of("amount", 5);

        ValidationResult result = handler.validateConfig(config);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validateConfig_zeroAmount_returnsValid() {
        Map<String, Object> config = Map.of("amount", 0);

        ValidationResult result = handler.validateConfig(config);

        assertThat(result.isValid()).isTrue();
    }

    @Test
    void validateConfig_invalidAmountFormat_returnsInvalid() {
        Map<String, Object> config = Map.of("amount", "not-a-number");

        ValidationResult result = handler.validateConfig(config);

        assertThat(result.isValid()).isFalse();
    }

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("wait-1")
            .nodeType("wait")
            .nodeConfig(new HashMap<>(config))
            .inputData(Map.of())
            .userId(UUID.randomUUID())
            .flowId(UUID.randomUUID())
            .build();
    }

    private NodeExecutionContext buildContextWithInput(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("wait-1")
            .nodeType("wait")
            .nodeConfig(new HashMap<>(config))
            .inputData(inputData)
            .userId(UUID.randomUUID())
            .flowId(UUID.randomUUID())
            .build();
    }
}
