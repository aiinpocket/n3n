package com.aiinpocket.n3n.execution.handler.handlers.integrations;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class PostgresNodeHandlerTest {

    private PostgresNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PostgresNodeHandler();
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {
        @Test
        void getType_returnsPostgres() { assertThat(handler.getType()).isEqualTo("postgres"); }

        @Test
        void getDisplayName_returnsPostgreSQL() { assertThat(handler.getDisplayName()).isEqualTo("PostgreSQL"); }

        @Test
        void getCategory_returnsDatabase() { assertThat(handler.getCategory()).isEqualTo("Database"); }

        @Test
        void supportsAsync() { assertThat(handler.supportsAsync()).isTrue(); }

        @Test
        void getConfigSchema_containsProperties() { assertThat(handler.getConfigSchema()).containsKey("properties"); }

        @Test
        void getInterfaceDefinition_hasIO() {
            assertThat(handler.getInterfaceDefinition()).containsKey("inputs").containsKey("outputs");
        }
    }

    @Nested
    @DisplayName("Connection Errors")
    class ConnectionErrors {
        @Test
        void execute_invalidConnection_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "query");
            config.put("host", "invalid-host");
            config.put("port", 5432);
            config.put("database", "test");
            config.put("query", "SELECT 1");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isFalse();
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID()).nodeId("postgres-1").nodeType("postgres")
                .nodeConfig(new HashMap<>(config)).inputData(inputData)
                .userId(UUID.randomUUID()).flowId(UUID.randomUUID()).build();
    }
}
