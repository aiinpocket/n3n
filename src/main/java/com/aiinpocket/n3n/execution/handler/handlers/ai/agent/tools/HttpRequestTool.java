package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
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
        .followRedirects(HttpClient.Redirect.NEVER)
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
                if (url == null || url.isBlank()) {
                    return ToolResult.failure("URL is required");
                }

                // SSRF protection: validate URL before sending request
                if (!isUrlSafe(url)) {
                    return ToolResult.failure("URL is not allowed: access to internal/private addresses is blocked");
                }

                String method = (String) parameters.getOrDefault("method", "GET");
                Map<String, String> headers = (Map<String, String>) parameters.getOrDefault("headers", Map.of());
                String body = (String) parameters.get("body");
                int timeout = Math.min(((Number) parameters.getOrDefault("timeout", 30)).intValue(), 120);

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

    /**
     * Validate URL to prevent SSRF attacks.
     * Blocks access to localhost, private networks, cloud metadata endpoints.
     */
    private boolean isUrlSafe(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();

            // Only allow HTTP/HTTPS
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                log.warn("SSRF blocked: non-HTTP scheme: {}", scheme);
                return false;
            }

            if (host == null || host.isBlank()) {
                return false;
            }

            // Check blocked hostnames
            String lowerHost = host.toLowerCase();
            if (lowerHost.equals("localhost") || lowerHost.equals("0.0.0.0") ||
                lowerHost.equals("::1") || lowerHost.endsWith(".internal") ||
                lowerHost.equals("metadata.google.internal") ||
                lowerHost.equals("169.254.169.254")) {
                log.warn("SSRF blocked: hostname {}", host);
                return false;
            }

            // Resolve hostname and check IP addresses
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress addr : addresses) {
                if (isPrivateOrReservedIP(addr)) {
                    log.warn("SSRF blocked: {} resolves to private/reserved IP {}", host, addr.getHostAddress());
                    return false;
                }
            }

            return true;
        } catch (URISyntaxException e) {
            log.warn("SSRF blocked: invalid URL syntax");
            return false;
        } catch (UnknownHostException e) {
            log.warn("SSRF blocked: cannot resolve host");
            return false;
        }
    }

    /**
     * Check if an IP address is private or reserved (RFC 1918, link-local, loopback, etc.)
     */
    private boolean isPrivateOrReservedIP(InetAddress addr) {
        byte[] ip = addr.getAddress();

        if (ip.length == 4) {
            int b0 = ip[0] & 0xFF;
            int b1 = ip[1] & 0xFF;

            // 127.0.0.0/8 (loopback)
            if (b0 == 127) return true;
            // 10.0.0.0/8 (private)
            if (b0 == 10) return true;
            // 172.16.0.0/12 (private)
            if (b0 == 172 && b1 >= 16 && b1 <= 31) return true;
            // 192.168.0.0/16 (private)
            if (b0 == 192 && b1 == 168) return true;
            // 169.254.0.0/16 (link-local / cloud metadata)
            if (b0 == 169 && b1 == 254) return true;
            // 0.0.0.0/8
            if (b0 == 0) return true;
            // 100.64.0.0/10 (carrier-grade NAT)
            if (b0 == 100 && b1 >= 64 && b1 <= 127) return true;
            // 224.0.0.0/4 (multicast)
            if (b0 >= 224 && b0 <= 239) return true;
            // 240.0.0.0/4 (reserved)
            if (b0 >= 240) return true;
        }

        // IPv6 checks
        if (ip.length == 16) {
            int b0 = ip[0] & 0xFF;
            // fc00::/7 (unique local)
            if (b0 == 0xFC || b0 == 0xFD) return true;
        }

        return addr.isLoopbackAddress() || addr.isSiteLocalAddress() ||
               addr.isLinkLocalAddress() || addr.isAnyLocalAddress();
    }

    private String truncateIfNeeded(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "... (truncated)";
    }
}
