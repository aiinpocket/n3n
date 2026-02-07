package com.aiinpocket.n3n.execution.handler.handlers.gcp;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class BigQueryNodeHandlerTest {
    private BigQueryNodeHandler handler;
    @BeforeEach void setUp() { handler = new BigQueryNodeHandler(new ObjectMapper()); }

    @Nested @DisplayName("Basic Properties")
    class BasicProperties {
        @Test void getType() { assertThat(handler.getType()).isEqualTo("bigQuery"); }
        @Test void getDisplayName() { assertThat(handler.getDisplayName()).contains("BigQuery"); }
        @Test void getConfigSchema() { assertThat(handler.getConfigSchema()).containsKey("properties"); }
        @Test void getInterfaceDefinition() { assertThat(handler.getInterfaceDefinition()).containsKey("inputs").containsKey("outputs"); }
        @Test void getResources() { assertThat(handler.getResources()).isNotEmpty(); }
    }

    @Nested @DisplayName("Validation")
    class Validation {
        @Test void execute_missingCredential_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "query"); config.put("operation", "execute");
            var result = handler.execute(NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID()).nodeId("bq-1").nodeType("bigquery")
                    .nodeConfig(new HashMap<>(config)).userId(UUID.randomUUID()).flowId(UUID.randomUUID()).build());
            assertThat(result.isSuccess()).isFalse();
        }
    }
}
