package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class EmailTriggerHandlerTest {

    private EmailTriggerHandler handler;

    @BeforeEach
    void setUp() {
        handler = new EmailTriggerHandler();
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {
        @Test
        void getType_returnsEmailTrigger() {
            assertThat(handler.getType()).isEqualTo("emailTrigger");
        }

        @Test
        void getDisplayName() {
            assertThat(handler.getDisplayName()).contains("Email");
        }

        @Test
        void getCategory_returnsTriggers() {
            assertThat(handler.getCategory()).isEqualTo("Triggers");
        }

        @Test
        void getConfigSchema_hasProperties() {
            assertThat(handler.getConfigSchema()).containsKey("properties");
        }

        @Test
        void getInterfaceDefinition_hasOutputs() {
            assertThat(handler.getInterfaceDefinition()).containsKey("outputs");
        }
    }

    @Nested
    @DisplayName("Execution")
    class Execution {
        @Test
        void execute_noTriggerInput_returnsSampleData() {
            NodeExecutionContext context = buildContext(new HashMap<>(), null, null);

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsKey("from");
            assertThat(result.getOutput()).containsKey("subject");
            assertThat(result.getOutput()).containsKey("body");
        }

        @Test
        void execute_withTriggerInput_usesProvidedData() {
            Map<String, Object> emailData = new HashMap<>();
            emailData.put("from", "test@example.com");
            emailData.put("subject", "Custom Subject");
            emailData.put("body", "Custom body");

            Map<String, Object> globalContext = new HashMap<>();
            globalContext.put("triggerInput", emailData);

            NodeExecutionContext context = buildContext(new HashMap<>(), null, globalContext);

            NodeExecutionResult result = handler.execute(context);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("from", "test@example.com");
            assertThat(result.getOutput()).containsEntry("subject", "Custom Subject");
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData, Map<String, Object> globalContext) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("emailTrigger-1")
                .nodeType("emailTrigger")
                .nodeConfig(new HashMap<>(config))
                .inputData(inputData)
                .globalContext(globalContext)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
