package com.aiinpocket.n3n.execution.handler.handlers.integrations;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class MySQLNodeHandlerTest {

    private MySQLNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new MySQLNodeHandler();
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {
        @Test
        void getType_returnsMysql() { assertThat(handler.getType()).isEqualTo("mysql"); }

        @Test
        void getDisplayName_returnsMySQL() { assertThat(handler.getDisplayName()).isEqualTo("MySQL"); }

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
            config.put("port", 3306);
            config.put("database", "test");
            config.put("query", "SELECT 1");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isFalse();
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID()).nodeId("mysql-1").nodeType("mysql")
                .nodeConfig(new HashMap<>(config)).inputData(inputData)
                .userId(UUID.randomUUID()).flowId(UUID.randomUUID()).build();
    }
}
