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

class ThreadsNodeHandlerTest {

    private ThreadsNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ThreadsNodeHandler(new ObjectMapper());
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {
        @Test
        void getType_returnsThreads() { assertThat(handler.getType()).isEqualTo("threads"); }

        @Test
        void getDisplayName() { assertThat(handler.getDisplayName()).contains("Threads"); }

        @Test
        void getConfigSchema_containsProperties() { assertThat(handler.getConfigSchema()).containsKey("properties"); }

        @Test
        void getInterfaceDefinition_hasIO() {
            assertThat(handler.getInterfaceDefinition()).containsKey("inputs").containsKey("outputs");
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {
        @Test
        void execute_missingCredential_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "thread");
            config.put("operation", "create");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isFalse();
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID()).nodeId("threads-1").nodeType("threads")
                .nodeConfig(new HashMap<>(config)).inputData(inputData)
                .userId(UUID.randomUUID()).flowId(UUID.randomUUID()).build();
    }
}
