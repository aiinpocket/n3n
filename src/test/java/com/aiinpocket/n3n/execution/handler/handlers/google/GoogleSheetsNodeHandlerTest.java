package com.aiinpocket.n3n.execution.handler.handlers.google;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class GoogleSheetsNodeHandlerTest {
    private GoogleSheetsNodeHandler handler;
    @BeforeEach void setUp() { handler = new GoogleSheetsNodeHandler(new ObjectMapper()); }

    @Nested @DisplayName("Basic Properties")
    class BasicProperties {
        @Test void getType() { assertThat(handler.getType()).isEqualTo("googleSheets"); }
        @Test void getDisplayName() { assertThat(handler.getDisplayName()).contains("Sheets"); }
        @Test void getConfigSchema() { assertThat(handler.getConfigSchema()).containsKey("properties"); }
        @Test void getInterfaceDefinition() { assertThat(handler.getInterfaceDefinition()).containsKey("inputs").containsKey("outputs"); }
        @Test void getResources() { assertThat(handler.getResources()).isNotEmpty(); }
    }

    @Nested @DisplayName("Validation")
    class Validation {
        @Test void execute_missingCredential_fails() {
            Map<String, Object> config = new HashMap<>();
            config.put("resource", "sheet"); config.put("operation", "read");
            var result = handler.execute(NodeExecutionContext.builder()
                    .executionId(UUID.randomUUID()).nodeId("sheets-1").nodeType("sheets")
                    .nodeConfig(new HashMap<>(config)).userId(UUID.randomUUID()).flowId(UUID.randomUUID()).build());
            assertThat(result.isSuccess()).isFalse();
        }
    }
}
