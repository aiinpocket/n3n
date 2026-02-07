package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class TriggerNodeHandlerTest {

    private TriggerNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TriggerNodeHandler();
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {
        @Test
        void getType_returnsTrigger() {
            assertThat(handler.getType()).isEqualTo("trigger");
        }

        @Test
        void getDisplayName_returnsManualTrigger() {
            assertThat(handler.getDisplayName()).isEqualTo("Manual Trigger");
        }

        @Test
        void getCategory_returnsTriggers() {
            assertThat(handler.getCategory()).isEqualTo("Triggers");
        }

        @Test
        void isTrigger_returnsTrue() {
            assertThat(handler.isTrigger()).isTrue();
        }

        @Test
        void getConfigSchema_containsProperties() {
            var schema = handler.getConfigSchema();
            assertThat(schema).containsKey("properties");
        }

        @Test
        void getInterfaceDefinition_hasEmptyInputsAndOutputs() {
            var iface = handler.getInterfaceDefinition();
            assertThat(iface).containsKey("inputs");
            assertThat(iface).containsKey("outputs");
        }
    }

    @Nested
    @DisplayName("Execution")
    class Execution {
        @Test
        void execute_withInputData_passesThrough() {
            Map<String, Object> input = new HashMap<>(Map.of("key", "value", "num", 42));
            NodeExecutionContext context = buildContext(new HashMap<>(), input);

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("key", "value");
            assertThat(result.getOutput()).containsEntry("num", 42);
        }

        @Test
        @SuppressWarnings("unchecked")
        void execute_withEmptyInput_returnsTriggeredOutput() {
            NodeExecutionContext context = buildContext(new HashMap<>(), new HashMap<>());

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            // createOutput wraps in "data" key
            assertThat(result.getOutput()).containsKey("data");
            Map<String, Object> data = (Map<String, Object>) result.getOutput().get("data");
            assertThat(data).containsEntry("triggered", true);
            assertThat(data).containsKey("timestamp");
        }

        @Test
        @SuppressWarnings("unchecked")
        void execute_withNullInput_returnsTriggeredOutput() {
            NodeExecutionContext context = buildContext(new HashMap<>(), null);

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("data");
            Map<String, Object> data = (Map<String, Object>) result.getOutput().get("data");
            assertThat(data).containsEntry("triggered", true);
        }

        @Test
        void execute_withComplexInput_passesThrough() {
            Map<String, Object> input = new HashMap<>();
            input.put("nested", Map.of("key", "value"));
            input.put("list", List.of(1, 2, 3));

            NodeExecutionContext context = buildContext(new HashMap<>(), input);
            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("nested");
            assertThat(result.getOutput()).containsKey("list");
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("trigger-1")
                .nodeType("trigger")
                .nodeConfig(config)
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
