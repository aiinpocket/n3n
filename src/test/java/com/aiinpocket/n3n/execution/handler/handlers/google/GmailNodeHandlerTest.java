package com.aiinpocket.n3n.execution.handler.handlers.google;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class GmailNodeHandlerTest {
    private GmailNodeHandler handler;

    @BeforeEach
    void setUp() { handler = new GmailNodeHandler(new ObjectMapper()); }

    @Nested @DisplayName("Basic Properties")
    class BasicProperties {
        @Test void getType() { assertThat(handler.getType()).isEqualTo("gmail"); }
        @Test void getDisplayName() { assertThat(handler.getDisplayName()).contains("Gmail"); }
        @Test void getConfigSchema() { assertThat(handler.getConfigSchema()).containsKey("properties"); }
        @Test void getInterfaceDefinition() { assertThat(handler.getInterfaceDefinition()).containsKey("inputs").containsKey("outputs"); }
        @Test void getResources() { assertThat(handler.getResources()).isNotEmpty(); }
    }

    @Nested @DisplayName("Validation")
    class Validation {
        @Test void execute_missingCredential_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "email"); config.put("operation", "send");
            assertThat(handler.execute(buildContext(config)).isSuccess()).isFalse();
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID()).nodeId("gmail-1").nodeType("gmail")
                .nodeConfig(new HashMap<>(config)).userId(UUID.randomUUID()).flowId(UUID.randomUUID()).build();
    }
}
