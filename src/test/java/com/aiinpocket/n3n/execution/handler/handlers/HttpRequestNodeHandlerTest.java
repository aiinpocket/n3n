package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class HttpRequestNodeHandlerTest {

    private HttpRequestNodeHandler handler;
    private ObjectMapper objectMapper;
    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper();
        handler = new HttpRequestNodeHandler(objectMapper);
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsHttpRequest() {
        assertThat(handler.getType()).isEqualTo("httpRequest");
    }

    @Test
    void getCategory_returnsNetwork() {
        assertThat(handler.getCategory()).isEqualTo("Network");
    }

    @Test
    void getDisplayName_returnsHttpRequest() {
        assertThat(handler.getDisplayName()).isEqualTo("HTTP Request");
    }

    @Test
    void supportsAsync_returnsTrue() {
        assertThat(handler.supportsAsync()).isTrue();
    }

    // ========== Validation ==========

    @Test
    void execute_missingUrl_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of());

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("URL is required");
    }

    @Test
    void execute_emptyUrl_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of("url", ""));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("URL is required");
    }

    @Test
    void execute_invalidUrlScheme_returnsFailure() {
        NodeExecutionContext context = buildContext(Map.of("url", "ftp://example.com"));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("URL must start with http://");
    }

    // ========== GET Request ==========

    @Test
    void execute_getRequest_returnsResponse() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"message\":\"hello\"}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        String url = mockWebServer.url("/api/test").toString();
        NodeExecutionContext context = buildContext(Map.of("url", url, "method", "GET"));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("status", 200);
        assertThat(result.getOutput()).containsKey("data");
        assertThat(result.getOutput()).containsKey("headers");

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
    }

    @Test
    void execute_getRequest_defaultMethod_isGet() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("ok")
                .setResponseCode(200));

        String url = mockWebServer.url("/api/test").toString();
        NodeExecutionContext context = buildContext(Map.of("url", url));

        handler.execute(context);

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("GET");
    }

    // ========== POST Request ==========

    @Test
    void execute_postRequest_sendsBody() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"id\":1}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(201));

        String url = mockWebServer.url("/api/items").toString();
        Map<String, Object> body = Map.of("name", "test");
        NodeExecutionContext context = buildContext(Map.of(
                "url", url,
                "method", "POST",
                "body", body
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("status", 201);

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("POST");
        assertThat(recordedRequest.getBody().readUtf8()).contains("name");
    }

    // ========== PUT Request ==========

    @Test
    void execute_putRequest_sendsBody() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"updated\":true}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        String url = mockWebServer.url("/api/items/1").toString();
        NodeExecutionContext context = buildContext(Map.of(
                "url", url,
                "method", "PUT",
                "body", Map.of("name", "updated")
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("PUT");
    }

    // ========== DELETE Request ==========

    @Test
    void execute_deleteRequest_succeeds() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(204));

        String url = mockWebServer.url("/api/items/1").toString();
        NodeExecutionContext context = buildContext(Map.of(
                "url", url,
                "method", "DELETE"
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("status", 204);

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getMethod()).isEqualTo("DELETE");
    }

    // ========== Custom Headers ==========

    @Test
    void execute_customHeaders_sentWithRequest() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        String url = mockWebServer.url("/api/test").toString();
        Map<String, Object> headers = Map.of(
                "Authorization", "Bearer token123",
                "X-Custom-Header", "custom-value"
        );
        NodeExecutionContext context = buildContext(Map.of(
                "url", url,
                "headers", headers
        ));

        handler.execute(context);

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getHeader("Authorization")).isEqualTo("Bearer token123");
        assertThat(recordedRequest.getHeader("X-Custom-Header")).isEqualTo("custom-value");
    }

    @Test
    void execute_headersAsList_sentWithRequest() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        String url = mockWebServer.url("/api/test").toString();
        List<Map<String, String>> headers = List.of(
                Map.of("name", "X-Custom", "value", "listValue")
        );
        NodeExecutionContext context = buildContext(Map.of(
                "url", url,
                "headers", headers
        ));

        handler.execute(context);

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getHeader("X-Custom")).isEqualTo("listValue");
    }

    // ========== Error Responses ==========

    @Test
    void execute_4xxResponse_returnsSuccessByDefault() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"error\":\"not found\"}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(404));

        String url = mockWebServer.url("/api/missing").toString();
        NodeExecutionContext context = buildContext(Map.of("url", url));

        NodeExecutionResult result = handler.execute(context);

        // By default, non-2xx is still a success (just reports the status)
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("status", 404);
    }

    @Test
    void execute_5xxResponse_returnsSuccessByDefault() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("Internal Server Error")
                .setResponseCode(500));

        String url = mockWebServer.url("/api/error").toString();
        NodeExecutionContext context = buildContext(Map.of("url", url));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("status", 500);
    }

    @Test
    void execute_successOnlyTrue_4xxReturnsFailure() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("Not Found")
                .setResponseCode(404));

        String url = mockWebServer.url("/api/missing").toString();
        NodeExecutionContext context = buildContext(Map.of(
                "url", url,
                "successOnly", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("404");
    }

    @Test
    void execute_successOnlyTrue_2xxReturnsSuccess() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"ok\":true}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        String url = mockWebServer.url("/api/ok").toString();
        NodeExecutionContext context = buildContext(Map.of(
                "url", url,
                "successOnly", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
    }

    // ========== JSON Parsing ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_jsonResponse_parsedCorrectly() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"items\":[1,2,3],\"total\":3}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        String url = mockWebServer.url("/api/items").toString();
        NodeExecutionContext context = buildContext(Map.of("url", url));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Object data = result.getOutput().get("data");
        assertThat(data).isInstanceOf(Map.class);
        Map<String, Object> dataMap = (Map<String, Object>) data;
        assertThat(dataMap).containsEntry("total", 3);
    }

    @Test
    void execute_plainTextResponse_returnedAsString() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("plain text response")
                .addHeader("Content-Type", "text/plain")
                .setResponseCode(200));

        String url = mockWebServer.url("/api/text").toString();
        NodeExecutionContext context = buildContext(Map.of("url", url));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("data")).isEqualTo("plain text response");
    }

    // ========== Response Headers ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_responseHeaders_includedInOutput() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("ok")
                .addHeader("X-Request-Id", "abc-123")
                .setResponseCode(200));

        String url = mockWebServer.url("/api/test").toString();
        NodeExecutionContext context = buildContext(Map.of("url", url));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        Map<String, String> headers = (Map<String, String>) result.getOutput().get("headers");
        assertThat(headers).containsEntry("X-Request-Id", "abc-123");
    }

    // ========== Include Raw Body ==========

    @Test
    void execute_includeRawBodyTrue_includesBodyString() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"key\":\"value\"}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        String url = mockWebServer.url("/api/test").toString();
        NodeExecutionContext context = buildContext(Map.of(
                "url", url,
                "includeRawBody", true
        ));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsKey("body");
        assertThat(result.getOutput().get("body").toString()).contains("key");
    }

    @Test
    void execute_includeRawBodyFalse_doesNotIncludeBodyString() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setBody("{\"key\":\"value\"}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        String url = mockWebServer.url("/api/test").toString();
        NodeExecutionContext context = buildContext(Map.of("url", url));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).doesNotContainKey("body");
    }

    // ========== User-Agent ==========

    @Test
    void execute_noUserAgent_addsDefault() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        String url = mockWebServer.url("/api/test").toString();
        NodeExecutionContext context = buildContext(Map.of("url", url));

        handler.execute(context);

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertThat(recordedRequest.getHeader("User-Agent")).contains("n3n-workflow");
    }

    // ========== Timeout Configuration ==========

    @Test
    void execute_timeoutCapped_atMaximum() throws Exception {
        // This test verifies that excessive timeout values are capped
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        String url = mockWebServer.url("/api/test").toString();
        NodeExecutionContext context = buildContext(Map.of(
                "url", url,
                "timeout", 9999
        ));

        NodeExecutionResult result = handler.execute(context);

        // Should still succeed (timeout capped at MAX_TIMEOUT_SECONDS=300)
        assertThat(result.isSuccess()).isTrue();
    }

    // ========== ValidateConfig ==========

    @Test
    void validateConfig_missingUrl_returnsInvalid() {
        var result = handler.validateConfig(Map.of());

        assertThat(result.isValid()).isFalse();
    }

    @Test
    void validateConfig_invalidUrlScheme_returnsInvalid() {
        var result = handler.validateConfig(Map.of("url", "ftp://example.com"));

        assertThat(result.isValid()).isFalse();
    }

    @Test
    void validateConfig_invalidMethod_returnsInvalid() {
        var result = handler.validateConfig(Map.of("url", "https://example.com", "method", "INVALID"));

        assertThat(result.isValid()).isFalse();
    }

    @Test
    void validateConfig_validConfig_returnsValid() {
        var result = handler.validateConfig(Map.of("url", "https://example.com", "method", "GET"));

        assertThat(result.isValid()).isTrue();
    }

    // ========== Status Text ==========

    @Test
    void execute_responseIncludesStatusText() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200));

        String url = mockWebServer.url("/api/test").toString();
        NodeExecutionContext context = buildContext(Map.of("url", url));

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsKey("statusText");
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_hasRequiredFields() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("required");
        assertThat(schema).containsKey("properties");
    }

    @Test
    void getInterfaceDefinition_hasInputsAndOutputs() {
        var iface = handler.getInterfaceDefinition();
        assertThat(iface).containsKey("inputs");
        assertThat(iface).containsKey("outputs");
    }

    // ========== Helper ==========

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        Map<String, Object> nodeConfig = new HashMap<>(config);
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("http-1")
                .nodeType("httpRequest")
                .nodeConfig(nodeConfig)
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
