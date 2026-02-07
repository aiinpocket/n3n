package com.aiinpocket.n3n.execution.handler.handlers.browser;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * Handles browser session lifecycle operations: create, close, list.
 */
@Slf4j
final class BrowserSessionOperations {

    private BrowserSessionOperations() {}

    static NodeExecutionResult execute(
            String host, int port, String sessionId, String operation,
            NodeExecutionContext context,
            OkHttpClient httpClient, ObjectMapper objectMapper,
            ConcurrentMap<String, BrowserNodeHandler.BrowserSession> sessions) throws IOException {
        return switch (operation) {
            case "create" -> createSession(host, port, sessionId, context, httpClient, objectMapper, sessions);
            case "close" -> closeSession(sessionId, httpClient, sessions);
            case "list" -> listSessions(host, port, httpClient, objectMapper);
            default -> NodeExecutionResult.failure("Unknown session operation: " + operation);
        };
    }

    private static NodeExecutionResult createSession(
            String host, int port, String sessionId, NodeExecutionContext context,
            OkHttpClient httpClient, ObjectMapper objectMapper,
            ConcurrentMap<String, BrowserNodeHandler.BrowserSession> sessions) throws IOException {

        String baseUrl = "http://" + host + ":" + port;
        String url = getStringConfig(context, "url", "about:blank");

        Request createRequest = new Request.Builder()
            .url(baseUrl + "/json/new?" + java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8))
            .put(RequestBody.create("", MediaType.parse("application/json")))
            .build();

        try (Response response = httpClient.newCall(createRequest).execute()) {
            if (!response.isSuccessful()) {
                return NodeExecutionResult.failure("Failed to create browser session: " + response.code());
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            JsonNode json = objectMapper.readTree(responseBody);

            String targetId = json.path("id").asText();
            String wsUrl = json.path("webSocketDebuggerUrl").asText();

            BrowserNodeHandler.BrowserSession session = new BrowserNodeHandler.BrowserSession(targetId, wsUrl);
            sessions.put(sessionId, session);

            Map<String, Object> output = new HashMap<>();
            output.put("success", true);
            output.put("sessionId", sessionId);
            output.put("targetId", targetId);
            output.put("webSocketUrl", wsUrl);

            log.info("Browser session created: {}", sessionId);
            return NodeExecutionResult.success(output);
        }
    }

    private static NodeExecutionResult closeSession(
            String sessionId, OkHttpClient httpClient,
            ConcurrentMap<String, BrowserNodeHandler.BrowserSession> sessions) throws IOException {

        BrowserNodeHandler.BrowserSession session = sessions.remove(sessionId);
        if (session == null) {
            return NodeExecutionResult.failure("Session not found: " + sessionId);
        }

        String targetId = session.targetId();
        Request closeRequest = new Request.Builder()
            .url("http://localhost:9222/json/close/" + targetId)
            .get()
            .build();

        try (Response response = httpClient.newCall(closeRequest).execute()) {
            Map<String, Object> output = new HashMap<>();
            output.put("success", true);
            output.put("sessionId", sessionId);
            output.put("closed", true);

            log.info("Browser session closed: {}", sessionId);
            return NodeExecutionResult.success(output);
        }
    }

    private static NodeExecutionResult listSessions(
            String host, int port, OkHttpClient httpClient, ObjectMapper objectMapper) throws IOException {

        String baseUrl = "http://" + host + ":" + port;

        Request listRequest = new Request.Builder()
            .url(baseUrl + "/json/list")
            .get()
            .build();

        try (Response response = httpClient.newCall(listRequest).execute()) {
            if (!response.isSuccessful()) {
                return NodeExecutionResult.failure("Failed to list sessions: " + response.code());
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            List<?> targets = objectMapper.readValue(responseBody, List.class);

            Map<String, Object> output = new HashMap<>();
            output.put("success", true);
            output.put("targets", targets);
            output.put("count", targets.size());

            return NodeExecutionResult.success(output);
        }
    }

    // ===== Config helpers (duplicated from AbstractNodeHandler) =====

    private static String getStringConfig(NodeExecutionContext context, String key, String defaultValue) {
        Object value = context.getNodeConfig().get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
