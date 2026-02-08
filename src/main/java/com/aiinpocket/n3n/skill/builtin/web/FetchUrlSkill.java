package com.aiinpocket.n3n.skill.builtin.web;

import com.aiinpocket.n3n.skill.BuiltinSkill;
import com.aiinpocket.n3n.skill.SkillResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

/**
 * Skill to fetch content from a URL.
 * Pure HTTP request - no AI involved.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FetchUrlSkill implements BuiltinSkill {

    private final WebClient.Builder webClientBuilder;

    @Override
    public String getName() {
        return "fetch_url";
    }

    @Override
    public String getDisplayName() {
        return "Fetch URL";
    }

    @Override
    public String getDescription() {
        return "Fetch content from a URL using HTTP GET request";
    }

    @Override
    public String getCategory() {
        return "web";
    }

    @Override
    public String getIcon() {
        return "global";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "url", Map.of(
                    "type", "string",
                    "description", "The URL to fetch"
                ),
                "headers", Map.of(
                    "type", "object",
                    "description", "Optional HTTP headers",
                    "additionalProperties", Map.of("type", "string")
                ),
                "timeout", Map.of(
                    "type", "integer",
                    "description", "Request timeout in milliseconds",
                    "default", 30000
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
                "content", Map.of("type", "string", "description", "Response body"),
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

        try {
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) input.get("headers");

            WebClient webClient = webClientBuilder.build();
            WebClient.RequestHeadersSpec<?> request = webClient.get().uri(url);

            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    request = request.header(header.getKey(), header.getValue());
                }
            }

            String content = request
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return SkillResult.success(Map.of(
                "content", content != null ? content : "",
                "statusCode", 200
            ));

        } catch (Exception e) {
            log.error("Failed to fetch URL {}: {}", url, e.getMessage());
            return SkillResult.failure("FETCH_ERROR", "Failed to fetch URL");
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
