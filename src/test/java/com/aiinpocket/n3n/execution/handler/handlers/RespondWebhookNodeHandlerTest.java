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
class RespondWebhookNodeHandlerTest {

    private RespondWebhookNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new RespondWebhookNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsRespondWebhook() {
        assertThat(handler.getType()).isEqualTo("respondWebhook");
    }

    @Test
    void getCategory_returnsOutput() {
        assertThat(handler.getCategory()).isEqualTo("Output");
    }

    @Test
    void getDisplayName_returnsRespondToWebhook() {
        assertThat(handler.getDisplayName()).isEqualTo("Respond to Webhook");
    }

    // ========== Default Status 200 ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_defaultConfig_returns200WithJsonContentType() {
        NodeExecutionContext context = buildContext(Map.of(), Map.of());

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("statusCode", 200);
        assertThat(result.getOutput()).containsEntry("responseSent", true);

        Map<String, Object> webhookResponse = (Map<String, Object>) result.getOutput().get("webhookResponse");
        assertThat(webhookResponse).containsEntry("statusCode", 200);
        assertThat(webhookResponse).containsEntry("contentType", "application/json");
    }

    // ========== Custom Status Codes ==========

    @Test
    void execute_status201_returnsCreatedStatus() {
        Map<String, Object> config = new HashMap<>();
        config.put("statusCode", 201);

        NodeExecutionContext context = buildContext(config, Map.of());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("statusCode", 201);
    }

    @Test
    void execute_status301_returnsRedirectStatus() {
        Map<String, Object> config = new HashMap<>();
        config.put("statusCode", 301);

        NodeExecutionContext context = buildContext(config, Map.of());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("statusCode", 301);
    }

    @Test
    void execute_status400_returnsBadRequestStatus() {
        Map<String, Object> config = new HashMap<>();
        config.put("statusCode", 400);

        NodeExecutionContext context = buildContext(config, Map.of());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("statusCode", 400);
    }

    @Test
    void execute_status404_returnsNotFoundStatus() {
        Map<String, Object> config = new HashMap<>();
        config.put("statusCode", 404);

        NodeExecutionContext context = buildContext(config, Map.of());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("statusCode", 404);
    }

    @Test
    void execute_status500_returnsServerErrorStatus() {
        Map<String, Object> config = new HashMap<>();
        config.put("statusCode", 500);

        NodeExecutionContext context = buildContext(config, Map.of());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("statusCode", 500);
    }

    // ========== Invalid Status Codes ==========

    @Test
    void execute_statusCodeBelow100_returnsFailure() {
        Map<String, Object> config = new HashMap<>();
        config.put("statusCode", 99);

        NodeExecutionContext context = buildContext(config, Map.of());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Invalid HTTP status code");
        assertThat(result.getErrorMessage()).contains("99");
    }

    @Test
    void execute_statusCodeAbove599_returnsFailure() {
        Map<String, Object> config = new HashMap<>();
        config.put("statusCode", 600);

        NodeExecutionContext context = buildContext(config, Map.of());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Invalid HTTP status code");
        assertThat(result.getErrorMessage()).contains("600");
    }

    // ========== Custom Headers ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_customHeaders_mergedIntoResponse() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("X-Custom-Header", "custom-value");
        headers.put("X-Request-Id", "req-123");

        Map<String, Object> config = new HashMap<>();
        config.put("headers", headers);

        NodeExecutionContext context = buildContext(config, Map.of());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getOutput().get("webhookResponse");
        Map<String, Object> responseHeaders = (Map<String, Object>) webhookResponse.get("headers");
        assertThat(responseHeaders).containsEntry("X-Custom-Header", "custom-value");
        assertThat(responseHeaders).containsEntry("X-Request-Id", "req-123");
        assertThat(responseHeaders).containsEntry("Content-Type", "application/json");
    }

    // ========== JSON Body Mode ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_jsonBodyMode_returnsConfiguredBody() {
        Map<String, Object> body = Map.of("message", "success", "code", 0);

        Map<String, Object> config = new HashMap<>();
        config.put("bodyMode", "json");
        config.put("body", body);

        NodeExecutionContext context = buildContext(config, Map.of());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getOutput().get("webhookResponse");
        assertThat(webhookResponse.get("body")).isEqualTo(body);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_jsonBodyMode_noBodyConfigured_returnsEmptyMap() {
        Map<String, Object> config = new HashMap<>();
        config.put("bodyMode", "json");

        NodeExecutionContext context = buildContext(config, Map.of());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getOutput().get("webhookResponse");
        assertThat(webhookResponse.get("body")).isEqualTo(Map.of());
    }

    // ========== Text Body Mode ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_textBodyMode_returnsPlainTextBody() {
        Map<String, Object> config = new HashMap<>();
        config.put("bodyMode", "text");
        config.put("bodyText", "Hello, World!");

        NodeExecutionContext context = buildContext(config, Map.of());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getOutput().get("webhookResponse");
        assertThat(webhookResponse.get("body")).isEqualTo("Hello, World!");
    }

    // ========== Input Forward Mode ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_inputMode_forwardsInputDataAsBody() {
        Map<String, Object> config = new HashMap<>();
        config.put("bodyMode", "input");

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("result", "processed");
        inputData.put("count", 42);

        NodeExecutionContext context = buildContext(config, inputData);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getOutput().get("webhookResponse");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) webhookResponse.get("body");
        assertThat(body).containsEntry("result", "processed");
        assertThat(body).containsEntry("count", 42);
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_inputMode_nullInput_returnsEmptyMap() {
        Map<String, Object> config = new HashMap<>();
        config.put("bodyMode", "input");

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("webhook-1")
                .nodeType("respondWebhook")
                .nodeConfig(new HashMap<>(config))
                .inputData(null)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getOutput().get("webhookResponse");
        assertThat(webhookResponse.get("body")).isEqualTo(Map.of());
    }

    // ========== Auto Body Mode ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_autoMode_withInputData_usesInputAsBody() {
        Map<String, Object> config = new HashMap<>();
        config.put("bodyMode", "auto");

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("data", "auto-body");

        NodeExecutionContext context = buildContext(config, inputData);
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getOutput().get("webhookResponse");
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) webhookResponse.get("body");
        assertThat(body).containsEntry("data", "auto-body");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_autoMode_emptyInput_usesConfigBody() {
        Map<String, Object> configBody = Map.of("fallback", true);

        Map<String, Object> config = new HashMap<>();
        config.put("bodyMode", "auto");
        config.put("body", configBody);

        NodeExecutionContext context = buildContext(config, Map.of());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getOutput().get("webhookResponse");
        assertThat(webhookResponse.get("body")).isEqualTo(configBody);
    }

    // ========== Content Type ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_customContentType_setInResponse() {
        Map<String, Object> config = new HashMap<>();
        config.put("contentType", "text/html");

        NodeExecutionContext context = buildContext(config, Map.of());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> webhookResponse = (Map<String, Object>) result.getOutput().get("webhookResponse");
        assertThat(webhookResponse).containsEntry("contentType", "text/html");
        Map<String, Object> responseHeaders = (Map<String, Object>) webhookResponse.get("headers");
        assertThat(responseHeaders).containsEntry("Content-Type", "text/html");
    }

    // ========== Metadata Verification ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_metadataContainsWebhookResponse() {
        NodeExecutionContext context = buildContext(Map.of(), Map.of());
        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMetadata()).isNotNull();
        assertThat(result.getMetadata()).containsKey("webhookResponse");

        Map<String, Object> metaWebhookResponse = (Map<String, Object>) result.getMetadata().get("webhookResponse");
        assertThat(metaWebhookResponse).containsEntry("statusCode", 200);
    }

    // ========== Config Schema and Interface ==========

    @Test
    void getConfigSchema_containsExpectedProperties() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("statusCode");
        assertThat(properties).containsKey("contentType");
        assertThat(properties).containsKey("bodyMode");
        assertThat(properties).containsKey("headers");
    }

    @Test
    void getInterfaceDefinition_hasInputsAndOutputs() {
        var iface = handler.getInterfaceDefinition();
        assertThat(iface).containsKey("inputs");
        assertThat(iface).containsKey("outputs");
    }

    // ========== Helper ==========

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("webhook-1")
                .nodeType("respondWebhook")
                .nodeConfig(new HashMap<>(config))
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
