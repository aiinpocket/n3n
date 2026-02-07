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

class DiscordNodeHandlerTest {

    private DiscordNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new DiscordNodeHandler(new ObjectMapper());
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {
        @Test
        void getType_returnsDiscord() {
            assertThat(handler.getType()).isEqualTo("discord");
        }

        @Test
        void getDisplayName_returnsDiscord() {
            assertThat(handler.getDisplayName()).isEqualTo("Discord");
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
        void execute_missingRequiredConfig_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "message");
            config.put("operation", "send");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).isNotEmpty();
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("discord-1")
                .nodeType("discord")
                .nodeConfig(new HashMap<>(config))
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
