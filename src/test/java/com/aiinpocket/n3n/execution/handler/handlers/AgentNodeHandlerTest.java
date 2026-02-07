package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.gateway.node.NodeInvoker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AgentNodeHandlerTest {
    @Mock private NodeInvoker nodeInvoker;
    private AgentNodeHandler handler;

    @BeforeEach void setUp() { handler = new AgentNodeHandler(nodeInvoker); }

    @Nested @DisplayName("Basic Properties")
    class BasicProperties {
        @Test void getType() { assertThat(handler.getType()).isEqualTo("agent"); }
        @Test void getDisplayName() { assertThat(handler.getDisplayName()).isNotEmpty(); }
        @Test void getCategory() { assertThat(handler.getCategory()).isNotEmpty(); }
        @Test void getConfigSchema() { assertThat(handler.getConfigSchema()).containsKey("properties"); }
        @Test void getInterfaceDefinition() { assertThat(handler.getInterfaceDefinition()).containsKey("inputs").containsKey("outputs"); }
    }

    @Nested @DisplayName("Validation")
    class Validation {
        @Test void execute_missingDeviceId_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("command", "echo hello");
            NodeExecutionResult result = handler.execute(buildContext(config));
            assertThat(result.isSuccess()).isFalse();
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID()).nodeId("agent-1").nodeType("agent")
                .nodeConfig(new HashMap<>(config)).userId(UUID.randomUUID()).flowId(UUID.randomUUID()).build();
    }
}
