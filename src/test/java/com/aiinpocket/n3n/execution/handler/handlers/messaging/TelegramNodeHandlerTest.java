package com.aiinpocket.n3n.execution.handler.handlers.messaging;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class TelegramNodeHandlerTest {

    private TelegramNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TelegramNodeHandler(new ObjectMapper());
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {
        @Test
        void getType_returnsTelegram() {
            assertThat(handler.getType()).isEqualTo("telegram");
        }

        @Test
        void getDisplayName_returnsTelegram() {
            assertThat(handler.getDisplayName()).isEqualTo("Telegram");
        }

        @Test
        void getCategory_returnsMessaging() {
            assertThat(handler.getCategory()).isEqualTo("Messaging");
        }

        @Test
        void supportsAsync_returnsTrue() {
            assertThat(handler.supportsAsync()).isTrue();
        }

        @Test
        void getConfigSchema_containsProperties() {
            assertThat(handler.getConfigSchema()).containsKey("properties");
        }

        @Test
        void getInterfaceDefinition_hasInputsAndOutputs() {
            var iface = handler.getInterfaceDefinition();
            assertThat(iface).containsKey("inputs");
            assertThat(iface).containsKey("outputs");
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {
        @Test
        void execute_missingBotToken_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "message");
            config.put("operation", "send");
            // No bot token

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("token");
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("telegram-1")
                .nodeType("telegram")
                .nodeConfig(new HashMap<>(config))
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
