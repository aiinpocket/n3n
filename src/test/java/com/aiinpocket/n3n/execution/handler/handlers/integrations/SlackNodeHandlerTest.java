package com.aiinpocket.n3n.execution.handler.handlers.integrations;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class SlackNodeHandlerTest {

    private SlackNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new SlackNodeHandler(new ObjectMapper());
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {
        @Test
        void getType_returnsSlack() { assertThat(handler.getType()).isEqualTo("slack"); }

        @Test
        void getDisplayName_returnsSlack() { assertThat(handler.getDisplayName()).isEqualTo("Slack"); }

        @Test
        void getCategory_returnsCommunication() { assertThat(handler.getCategory()).isEqualTo("Communication"); }

        @Test
        void supportsAsync_returnsTrue() { assertThat(handler.supportsAsync()).isTrue(); }

        @Test
        void getConfigSchema_containsProperties() { assertThat(handler.getConfigSchema()).containsKey("properties"); }

        @Test
        void getInterfaceDefinition_hasIO() {
            var iface = handler.getInterfaceDefinition();
            assertThat(iface).containsKey("inputs").containsKey("outputs");
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {
        @Test
        void execute_missingToken_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "message");
            config.put("operation", "post");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isFalse();
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID()).nodeId("slack-1").nodeType("slack")
                .nodeConfig(new HashMap<>(config)).inputData(inputData)
                .userId(UUID.randomUUID()).flowId(UUID.randomUUID()).build();
    }
}
