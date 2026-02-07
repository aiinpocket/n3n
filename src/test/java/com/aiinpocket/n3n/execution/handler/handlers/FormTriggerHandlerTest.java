package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class FormTriggerHandlerTest {

    private FormTriggerHandler handler;

    @BeforeEach
    void setUp() {
        handler = new FormTriggerHandler();
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {
        @Test
        void getType_returnsFormTrigger() {
            assertThat(handler.getType()).isEqualTo("formTrigger");
        }

        @Test
        void getDisplayName_returnsFormTrigger() {
            assertThat(handler.getDisplayName()).isEqualTo("Form Trigger");
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
        void getInterfaceDefinition_hasOutputs() {
            var iface = handler.getInterfaceDefinition();
            assertThat(iface).containsKey("outputs");
        }
    }

    @Nested
    @DisplayName("Execution")
    class Execution {
        @Test
        void execute_withFormData_passesThrough() {
            Map<String, Object> input = new HashMap<>();
            input.put("name", "Alice");
            input.put("email", "alice@example.com");

            NodeExecutionContext context = buildContext(new HashMap<>(), input);
            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("name", "Alice");
            assertThat(result.getOutput()).containsEntry("email", "alice@example.com");
            assertThat(result.getOutput()).containsKey("_formTrigger");
        }

        @Test
        void execute_withEmptyInput_succeeds() {
            NodeExecutionContext context = buildContext(new HashMap<>(), new HashMap<>());
            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        void execute_withNullInput_succeeds() {
            NodeExecutionContext context = buildContext(new HashMap<>(), null);
            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("formTrigger-1")
                .nodeType("formTrigger")
                .nodeConfig(new HashMap<>(config))
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
