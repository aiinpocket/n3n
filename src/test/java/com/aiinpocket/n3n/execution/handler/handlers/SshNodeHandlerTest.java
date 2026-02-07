package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class SshNodeHandlerTest {
    private SshNodeHandler handler;
    @BeforeEach void setUp() { handler = new SshNodeHandler(); }

    @Nested @DisplayName("Basic Properties")
    class BasicProperties {
        @Test void getType() { assertThat(handler.getType()).isEqualTo("ssh"); }
        @Test void getDisplayName() { assertThat(handler.getDisplayName()).contains("SSH"); }
        @Test void getCategory() { assertThat(handler.getCategory()).isEqualTo("System"); }
        @Test void getConfigSchema() { assertThat(handler.getConfigSchema()).containsKey("properties"); }
        @Test void getInterfaceDefinition() { assertThat(handler.getInterfaceDefinition()).containsKey("inputs").containsKey("outputs"); }
    }

    @Nested @DisplayName("Connection Errors")
    class ConnectionErrors {
        @Test void execute_invalidHost_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("host", "invalid-host-xyz");
            config.put("command", "ls");
            config.put("username", "test");
            config.put("password", "test");
            NodeExecutionResult result = handler.execute(buildContext(config));
            assertThat(result.isSuccess()).isFalse();
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID()).nodeId("ssh-1").nodeType("ssh")
                .nodeConfig(new HashMap<>(config)).userId(UUID.randomUUID()).flowId(UUID.randomUUID()).build();
    }
}
