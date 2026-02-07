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
class RetryNodeHandlerTest {

    private RetryNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RetryNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsRetry() {
        assertThat(handler.getType()).isEqualTo("retry");
    }

    @Test
    void getCategory_returnsFlowControl() {
        assertThat(handler.getCategory()).isEqualTo("Flow Control");
    }

    @Test
    void getDisplayName_returnsRetry() {
        assertThat(handler.getDisplayName()).isEqualTo("Retry");
    }

    @Test
    void getDescription_isNotEmpty() {
        assertThat(handler.getDescription()).isNotBlank();
    }

    @Test
    void getIcon_returnsReload() {
        assertThat(handler.getIcon()).isEqualTo("reload");
    }

    // ========== Passthrough Tests ==========

    @Test
    void execute_noError_passThroughWithConfig() {
        NodeExecutionContext context = buildContext(
            Map.of("maxRetries", 3, "backoffStrategy", "exponential"),
            Map.of("data", "hello")
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("data", "hello");
        assertThat(result.getOutput()).containsKey("_retryConfig");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_noError_retryConfigContainsSettings() {
        NodeExecutionContext context = buildContext(
            Map.of("maxRetries", 5, "backoffStrategy", "linear"),
            Map.of("data", "test")
        );
        NodeExecutionResult result = handler.execute(context);

        Map<String, Object> retryConfig = (Map<String, Object>) result.getOutput().get("_retryConfig");
        assertThat(retryConfig).containsEntry("maxRetries", 5);
        assertThat(retryConfig).containsEntry("backoffStrategy", "linear");
    }

    // ========== Error Handling Tests ==========

    @Test
    void execute_withError_retriesAndAddsMetadata() {
        NodeExecutionContext context = buildContext(
            Map.of("maxRetries", 3, "initialDelayMs", 10, "backoffStrategy", "fixed"),
            Map.of("_error", "Connection timeout", "data", "payload")
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("_retryAttempt", 1);
        assertThat(result.getOutput()).containsEntry("data", "payload");
    }

    @Test
    void execute_withErrorAndRetryAttempt_incrementsAttempt() {
        NodeExecutionContext context = buildContext(
            Map.of("maxRetries", 3, "initialDelayMs", 10, "backoffStrategy", "fixed"),
            Map.of("_error", "Timeout", "_retryAttempt", 1, "data", "test")
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("_retryAttempt", 2);
    }

    @Test
    void execute_maxRetriesExceeded_fails() {
        NodeExecutionContext context = buildContext(
            Map.of("maxRetries", 3, "initialDelayMs", 10),
            Map.of("_error", "Final error", "_retryAttempt", 3)
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Max retries (3) exceeded");
    }

    // ========== Backoff Strategy Tests ==========

    @Test
    void execute_fixedBackoff_sameDelay() {
        NodeExecutionContext context = buildContext(
            Map.of("maxRetries", 5, "initialDelayMs", 10, "backoffStrategy", "fixed"),
            Map.of("_error", "error")
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(((Number) result.getOutput().get("_retryDelay")).longValue()).isEqualTo(10);
    }

    @Test
    void execute_linearBackoff_increasesLinearly() {
        // Attempt 2 → delay = initialDelay * attempt = 10 * 2 = 20
        NodeExecutionContext context = buildContext(
            Map.of("maxRetries", 5, "initialDelayMs", 10, "backoffStrategy", "linear"),
            Map.of("_error", "error", "_retryAttempt", 1)
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(((Number) result.getOutput().get("_retryDelay")).longValue()).isEqualTo(20);
    }

    @Test
    void execute_exponentialBackoff_doublesDelay() {
        NodeExecutionContext context = buildContext(
            Map.of("maxRetries", 5, "initialDelayMs", 10,
                   "backoffStrategy", "exponential", "backoffMultiplier", 2.0),
            Map.of("_error", "error")
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        // Attempt 1 → delay = 10 * 2^0 = 10
        assertThat(((Number) result.getOutput().get("_retryDelay")).longValue()).isEqualTo(10);
    }

    @Test
    void execute_jitterBackoff_isPositive() {
        NodeExecutionContext context = buildContext(
            Map.of("maxRetries", 5, "initialDelayMs", 100, "backoffStrategy", "jitter"),
            Map.of("_error", "error")
        );
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(((Number) result.getOutput().get("_retryDelay")).longValue()).isGreaterThanOrEqualTo(100);
    }

    // ========== Edge Cases ==========

    @Test
    void execute_emptyInput_noError_passthrough() {
        NodeExecutionContext context = buildContext(
            Map.of("maxRetries", 3),
            Map.of()
        );
        NodeExecutionResult result = handler.execute(context);
        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void getConfigSchema_hasProperties() {
        Map<String, Object> schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertThat(props).containsKey("maxRetries");
        assertThat(props).containsKey("backoffStrategy");
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("node1")
            .nodeType("retry")
            .nodeConfig(new HashMap<>(config))
            .inputData(new HashMap<>(inputData))
            .build();
    }
}
