package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterNodeHandlerTest {

    private RateLimiterNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RateLimiterNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsRateLimiter() {
        assertThat(handler.getType()).isEqualTo("rateLimiter");
    }

    @Test
    void getCategory_returnsFlowControl() {
        assertThat(handler.getCategory()).isEqualTo("Flow Control");
    }

    @Test
    void getDisplayName_returnsRateLimiter() {
        assertThat(handler.getDisplayName()).isEqualTo("Rate Limiter");
    }

    @Test
    void getDescription_isNotEmpty() {
        assertThat(handler.getDescription()).isNotBlank();
    }

    @Test
    void getIcon_returnsDashboard() {
        assertThat(handler.getIcon()).isEqualTo("dashboard");
    }

    // ========== Execution Tests ==========

    @Test
    void execute_underLimit_passesThrough() {
        NodeExecutionContext context = buildContext(Map.of(
            "maxRequests", 100,
            "windowMs", 1000,
            "mode", "delay"
        ), Map.of("data", "test"));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsKey("_rateLimiter");
    }

    @Test
    void execute_dropMode_dropsExcessItems() {
        // Use unique key to avoid interference from other tests
        String uniqueKey = "test-drop-" + UUID.randomUUID();

        // Quickly exhaust the limit
        for (int i = 0; i < 5; i++) {
            NodeExecutionContext ctx = buildContextWithKey(Map.of(
                "maxRequests", 5,
                "windowMs", 60000,
                "mode", "drop",
                "key", uniqueKey
            ), Map.of("data", "item" + i));
            handler.execute(ctx);
        }

        // Next one should be dropped
        NodeExecutionContext context = buildContextWithKey(Map.of(
            "maxRequests", 5,
            "windowMs", 60000,
            "mode", "drop",
            "key", uniqueKey
        ), Map.of("data", "overflow"));

        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("dropped")).isEqualTo(true);
    }

    @Test
    void execute_errorMode_failsOnOverflow() {
        String uniqueKey = "test-error-" + UUID.randomUUID();

        for (int i = 0; i < 3; i++) {
            NodeExecutionContext ctx = buildContextWithKey(Map.of(
                "maxRequests", 3,
                "windowMs", 60000,
                "mode", "error",
                "key", uniqueKey
            ), Map.of("data", "item" + i));
            handler.execute(ctx);
        }

        NodeExecutionContext context = buildContextWithKey(Map.of(
            "maxRequests", 3,
            "windowMs", 60000,
            "mode", "error",
            "key", uniqueKey
        ), Map.of("data", "overflow"));

        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Rate limit exceeded");
    }

    @Test
    void execute_passesInputData() {
        NodeExecutionContext context = buildContext(Map.of(
            "maxRequests", 100,
            "windowMs", 1000
        ), Map.of("key1", "value1", "key2", "value2"));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("key1", "value1");
    }

    @Test
    void getConfigSchema_hasProperties() {
        Map<String, Object> schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertThat(props).containsKey("maxRequests");
        assertThat(props).containsKey("windowMs");
        assertThat(props).containsKey("mode");
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("node-" + UUID.randomUUID())
            .nodeType("rateLimiter")
            .nodeConfig(new HashMap<>(config))
            .inputData(new HashMap<>(inputData))
            .build();
    }

    private NodeExecutionContext buildContextWithKey(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("node1")
            .nodeType("rateLimiter")
            .nodeConfig(new HashMap<>(config))
            .inputData(new HashMap<>(inputData))
            .build();
    }
}
