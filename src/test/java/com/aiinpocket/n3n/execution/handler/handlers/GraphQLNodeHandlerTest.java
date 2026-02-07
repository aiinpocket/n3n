package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class GraphQLNodeHandlerTest {

    private GraphQLNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GraphQLNodeHandler();
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {
        @Test
        void getType_returnsGraphql() {
            assertThat(handler.getType()).isEqualTo("graphql");
        }

        @Test
        void getDisplayName_returnsGraphQL() {
            assertThat(handler.getDisplayName()).isEqualTo("GraphQL");
        }

        @Test
        void getCategory_returnsCommunication() {
            assertThat(handler.getCategory()).isEqualTo("Communication");
        }

        @Test
        void getConfigSchema_containsRequiredFields() {
            var schema = handler.getConfigSchema();
            assertThat(schema).containsKey("properties");
            assertThat(schema).containsKey("required");
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
        void execute_missingEndpoint_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("query", "{ users { id } }");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("endpoint");
        }

        @Test
        void execute_missingQuery_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("endpoint", "https://example.com/graphql");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("query");
        }

        @Test
        void execute_emptyEndpoint_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("endpoint", "");
            config.put("query", "{ users { id } }");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isFalse();
        }

        @Test
        void execute_emptyQuery_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("endpoint", "https://example.com/graphql");
            config.put("query", "");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isFalse();
        }
    }

    @Nested
    @DisplayName("Connection Errors")
    class ConnectionErrors {
        @Test
        void execute_invalidUrl_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("endpoint", "not-a-valid-url");
            config.put("query", "{ users { id } }");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("failed");
        }

        @Test
        void execute_unreachableHost_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("endpoint", "https://unreachable.invalid/graphql");
            config.put("query", "{ users { id } }");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isFalse();
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("graphql-1")
                .nodeType("graphql")
                .nodeConfig(new HashMap<>(config))
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
