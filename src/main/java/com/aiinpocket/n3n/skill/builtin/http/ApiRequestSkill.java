package com.aiinpocket.n3n.skill.builtin.http;

import com.aiinpocket.n3n.skill.BuiltinSkill;
import com.aiinpocket.n3n.skill.SkillResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

/**
 * General-purpose HTTP API request skill.
 * Supports GET, POST, PUT, PATCH, DELETE methods.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiRequestSkill implements BuiltinSkill {

    private final WebClient.Builder webClientBuilder;

    @Override
    public String getName() {
        return "api_request";
    }

    @Override
    public String getDisplayName() {
        return "API Request";
    }

    @Override
    public String getDescription() {
        return "Make an HTTP API request with customizable method, headers, and body";
    }

    @Override
    public String getCategory() {
        return "http";
    }

    @Override
    public String getIcon() {
        return "api";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "url", Map.of(
                    "type", "string",
                    "description", "The API endpoint URL"
                ),
                "method", Map.of(
                    "type", "string",
                    "enum", List.of("GET", "POST", "PUT", "PATCH", "DELETE"),
                    "default", "GET",
                    "description", "HTTP method"
                ),
                "headers", Map.of(
                    "type", "object",
                    "description", "HTTP headers",
                    "additionalProperties", Map.of("type", "string")
                ),
                "body", Map.of(
                    "type", "object",
                    "description", "Request body (for POST/PUT/PATCH)"
                ),
                "queryParams", Map.of(
                    "type", "object",
                    "description", "Query parameters",
                    "additionalProperties", Map.of("type", "string")
                )
            ),
            "required", List.of("url")
        );
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "data", Map.of("type", "object", "description", "Response body"),
                "statusCode", Map.of("type", "integer", "description", "HTTP status code"),
                "headers", Map.of("type", "object", "description", "Response headers")
            )
        );
    }

    @Override
    public SkillResult execute(Map<String, Object> input) {
        String url = (String) input.get("url");
        if (url == null || url.isBlank()) {
            return SkillResult.failure("MISSING_URL", "URL is required");
        }

        // SSRF protection
        if (!isUrlSafe(url)) {
            return SkillResult.failure("BLOCKED_URL", "URL blocked: internal or restricted address");
        }

        String method = (String) input.getOrDefault("method", "GET");

        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) input.get("headers");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) input.get("body");

        @SuppressWarnings("unchecked")
        Map<String, String> queryParams = (Map<String, String>) input.get("queryParams");

        try {
            WebClient webClient = webClientBuilder.build();

            // Build URL with query params
            String finalUrl = url;
            if (queryParams != null && !queryParams.isEmpty()) {
                StringBuilder sb = new StringBuilder(url);
                sb.append(url.contains("?") ? "&" : "?");
                queryParams.forEach((k, v) -> sb.append(k).append("=").append(v).append("&"));
                finalUrl = sb.substring(0, sb.length() - 1);
            }

            HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());

            WebClient.RequestBodySpec requestSpec = webClient
                .method(httpMethod)
                .uri(finalUrl);

            // Add headers
            if (headers != null) {
                headers.forEach(requestSpec::header);
            }

            // Add body for methods that support it
            Mono<Map> responseMono;
            if (body != null && (httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT || httpMethod == HttpMethod.PATCH)) {
                responseMono = requestSpec
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class);
            } else {
                responseMono = requestSpec
                    .retrieve()
                    .bodyToMono(Map.class);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> responseData = responseMono.block();

            return SkillResult.success(Map.of(
                "data", responseData != null ? responseData : Map.of(),
                "statusCode", 200
            ));

        } catch (Exception e) {
            log.error("API request to {} failed: {}", url, e.getMessage());
            return SkillResult.failure("API_ERROR", "API request failed");
        }
    }

    /**
     * Validate whether URL is safe (SSRF protection)
     */
    private boolean isUrlSafe(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            String scheme = uri.getScheme();

            // Only allow HTTP/HTTPS
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                log.warn("Blocked non-HTTP(S) scheme: {}", scheme);
                return false;
            }

            if (host == null || host.isBlank()) {
                return false;
            }

            // Resolve IP address and check for private/reserved ranges
            try {
                InetAddress[] addresses = InetAddress.getAllByName(host);
                for (InetAddress addr : addresses) {
                    if (isPrivateOrReservedIP(addr)) {
                        log.warn("Blocked URL resolving to private/reserved IP: {} -> {}", host, addr.getHostAddress());
                        return false;
                    }
                }
            } catch (UnknownHostException e) {
                log.warn("Cannot resolve host: {}", host);
                return false;
            }

            return true;
        } catch (URISyntaxException e) {
            log.warn("Invalid URL syntax: {}", url);
            return false;
        }
    }

    /**
     * Check if IP address is private or reserved
     */
    private boolean isPrivateOrReservedIP(InetAddress addr) {
        byte[] ip = addr.getAddress();

        // IPv4 check
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

            // 169.254.0.0/16 (link-local, cloud metadata)
            if (b0 == 169 && b1 == 254) return true;

            // 0.0.0.0/8 (current network)
            if (b0 == 0) return true;
        }

        // IPv6 check
        if (ip.length == 16) {
            // ::1 (loopback)
            if (addr.isLoopbackAddress()) return true;

            // fe80::/10 (link-local)
            if (addr.isLinkLocalAddress()) return true;

            // fc00::/7 (unique local)
            int b0 = ip[0] & 0xFF;
            if (b0 == 0xFC || b0 == 0xFD) return true;
        }

        return addr.isLoopbackAddress() || addr.isSiteLocalAddress() ||
               addr.isLinkLocalAddress() || addr.isAnyLocalAddress();
    }
}
