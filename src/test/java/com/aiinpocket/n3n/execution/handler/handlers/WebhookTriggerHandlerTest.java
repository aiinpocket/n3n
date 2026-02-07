package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class WebhookTriggerHandlerTest {

    private WebhookTriggerHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WebhookTriggerHandler();
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {
        @Test
        void getType_returnsWebhookTrigger() {
            assertThat(handler.getType()).isEqualTo("webhookTrigger");
        }

        @Test
        void getDisplayName_returnsWebhookTrigger() {
            assertThat(handler.getDisplayName()).isEqualTo("Webhook Trigger");
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
        void execute_withRequestData_includesAllFields() {
            Map<String, Object> input = new HashMap<>();
            input.put("method", "POST");
            input.put("headers", Map.of("Content-Type", "application/json"));
            input.put("query", Map.of("param", "value"));
            input.put("body", Map.of("key", "data"));
            input.put("params", Map.of("id", "123"));

            NodeExecutionContext context = buildContext(new HashMap<>(), input);
            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("method", "POST");
            assertThat(result.getOutput()).containsEntry("triggerType", "webhook");
            assertThat(result.getOutput()).containsKey("triggeredAt");
            assertThat(result.getOutput()).containsKey("headers");
            assertThat(result.getOutput()).containsKey("query");
            assertThat(result.getOutput()).containsKey("body");
            assertThat(result.getOutput()).containsKey("params");
            assertThat(result.getOutput()).containsKey("data");
        }

        @Test
        void execute_withNullInput_returnsBasicOutput() {
            NodeExecutionContext context = buildContext(new HashMap<>(), null);
            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("triggerType", "webhook");
            assertThat(result.getOutput()).containsKey("triggeredAt");
        }

        @Test
        void execute_withMinimalInput_succeeds() {
            Map<String, Object> input = new HashMap<>();
            input.put("body", Map.of("msg", "hello"));

            NodeExecutionContext context = buildContext(new HashMap<>(), input);
            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("body");
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("webhook-1")
                .nodeType("webhookTrigger")
                .nodeConfig(new HashMap<>(config))
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
