package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class FtpNodeHandlerTest {
    private FtpNodeHandler handler;
    @BeforeEach void setUp() { handler = new FtpNodeHandler(); }

    @Nested @DisplayName("Basic Properties")
    class BasicProperties {
        @Test void getType() { assertThat(handler.getType()).isEqualTo("ftp"); }
        @Test void getDisplayName() { assertThat(handler.getDisplayName()).contains("FTP"); }
        @Test void getCategory() { assertThat(handler.getCategory()).isEqualTo("Communication"); }
        @Test void getConfigSchema() { assertThat(handler.getConfigSchema()).containsKey("properties"); }
        @Test void getInterfaceDefinition() { assertThat(handler.getInterfaceDefinition()).containsKey("inputs").containsKey("outputs"); }
    }

    @Nested @DisplayName("Operations")
    class Operations {
        @Test void execute_listOperation_returnsFiles() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "list");
            config.put("remotePath", "/data");
            NodeExecutionResult result = handler.execute(buildContext(config));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("operation", "list");
            assertThat(result.getOutput()).containsKey("files");
        }

        @Test void execute_uploadOperation_returnsStatus() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "upload");
            config.put("remotePath", "/uploads");
            config.put("localPath", "/tmp/file.txt");
            NodeExecutionResult result = handler.execute(buildContext(config));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("status", "uploaded");
        }

        @Test void execute_downloadOperation_returnsStatus() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "download");
            config.put("remotePath", "/file.txt");
            NodeExecutionResult result = handler.execute(buildContext(config));
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput()).containsEntry("status", "downloaded");
        }

        @Test void execute_deleteOperation_returnsStatus() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "delete");
            config.put("remotePath", "/old-file.txt");
            NodeExecutionResult result = handler.execute(buildContext(config));
            assertThat(result.isSuccess()).isTrue();
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID()).nodeId("ftp-1").nodeType("ftp")
                .nodeConfig(new HashMap<>(config)).userId(UUID.randomUUID()).flowId(UUID.randomUUID()).build();
    }
}
