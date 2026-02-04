package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP 請求工具
 * 讓 AI Agent 能夠發送 HTTP 請求
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HttpRequestTool implements AgentNodeTool {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    @Override
    public String getId() {
        return "http_request";
    }

    @Override
    public String getName() {
        return "HTTP Request";
    }

    @Override
    public String getDescription() {
        return "Send HTTP requests to external APIs. Supports GET, POST, PUT, DELETE methods. " +
               "Use this to fetch data from APIs, submit forms, or interact with web services.";
    }

    @Override
    public String getCategory() {
        return "network";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "url", Map.of(
                    "type", "string",
                    "description", "The URL to send the request to"
                ),
                "method", Map.of(
                    "type", "string",
                    "enum", List.of("GET", "POST", "PUT", "DELETE", "PATCH"),
                    "description", "HTTP method",
                    "default", "GET"
                ),
                "headers", Map.of(
                    "type", "object",
                    "description", "HTTP headers as key-value pairs",
                    "additionalProperties", Map.of("type", "string")
                ),
                "body", Map.of(
                    "type", "string",
                    "description", "Request body (for POST, PUT, PATCH)"
                ),
                "timeout", Map.of(
                    "type", "integer",
                    "description", "Request timeout in seconds",
                    "default", 30
                )
            ),
            "required", List.of("url")
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = (String) parameters.get("url");
                String method = (String) parameters.getOrDefault("method", "GET");
                Map<String, String> headers = (Map<String, String>) parameters.getOrDefault("headers", Map.of());
                String body = (String) parameters.get("body");
                int timeout = ((Number) parameters.getOrDefault("timeout", 30)).intValue();

                // 建立請求
                HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout));

                // 設定 headers
                headers.forEach(requestBuilder::header);

                // 設定方法和 body
                HttpRequest.BodyPublisher bodyPublisher = body != null
                    ? HttpRequest.BodyPublishers.ofString(body)
                    : HttpRequest.BodyPublishers.noBody();

                switch (method.toUpperCase()) {
                    case "GET" -> requestBuilder.GET();
                    case "POST" -> requestBuilder.POST(bodyPublisher);
                    case "PUT" -> requestBuilder.PUT(bodyPublisher);
                    case "DELETE" -> requestBuilder.DELETE();
                    case "PATCH" -> requestBuilder.method("PATCH", bodyPublisher);
                    default -> requestBuilder.GET();
                }

                HttpRequest request = requestBuilder.build();

                // 發送請求
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                // 建立結果
                Map<String, Object> data = new HashMap<>();
                data.put("statusCode", response.statusCode());
                data.put("headers", response.headers().map());
                data.put("body", response.body());

                String output = String.format("HTTP %s %s returned status %d\n\nResponse:\n%s",
                    method, url, response.statusCode(),
                    truncateIfNeeded(response.body(), 2000));

                log.debug("HTTP request completed: {} {} -> {}", method, url, response.statusCode());

                return ToolResult.success(output, data);

            } catch (Exception e) {
                log.error("HTTP request failed: {}", e.getMessage());
                return ToolResult.failure("HTTP request failed: " + e.getMessage());
            }
        });
    }

    private String truncateIfNeeded(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "... (truncated)";
    }
}
