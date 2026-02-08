package com.aiinpocket.n3n.skill.builtin.notify;

import com.aiinpocket.n3n.skill.BuiltinSkill;
import com.aiinpocket.n3n.skill.SkillResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

/**
 * Skill to send data to a webhook URL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SendWebhookSkill implements BuiltinSkill {

    private final WebClient.Builder webClientBuilder;

    @Override
    public String getName() {
        return "send_webhook";
    }

    @Override
    public String getDisplayName() {
        return "Send Webhook";
    }

    @Override
    public String getDescription() {
        return "Send data to a webhook URL via HTTP POST";
    }

    @Override
    public String getCategory() {
        return "notify";
    }

    @Override
    public String getIcon() {
        return "notification";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "url", Map.of(
                    "type", "string",
                    "description", "Webhook URL"
                ),
                "payload", Map.of(
                    "type", "object",
                    "description", "Data to send"
                ),
                "headers", Map.of(
                    "type", "object",
                    "description", "Optional HTTP headers",
                    "additionalProperties", Map.of("type", "string")
                )
            ),
            "required", List.of("url", "payload")
        );
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "success", Map.of("type", "boolean"),
                "statusCode", Map.of("type", "integer")
            )
        );
    }

    @Override
    public SkillResult execute(Map<String, Object> input) {
        String url = (String) input.get("url");
        Object payload = input.get("payload");

        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) input.get("headers");

        if (url == null || url.isBlank()) {
            return SkillResult.failure("MISSING_URL", "Webhook URL is required");
        }

        if (payload == null) {
            return SkillResult.failure("MISSING_PAYLOAD", "Payload is required");
        }

        // SSRF protection
        if (!isUrlSafe(url)) {
            return SkillResult.failure("BLOCKED_URL", "URL blocked: internal or restricted address");
        }

        try {
            WebClient webClient = webClientBuilder.build();

            WebClient.RequestBodySpec request = webClient
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON);

            if (headers != null) {
                headers.forEach(request::header);
            }

            request.bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return SkillResult.success(Map.of(
                "success", true,
                "statusCode", 200
            ));

        } catch (Exception e) {
            log.error("Failed to send webhook to {}: {}", url, e.getMessage());
            return SkillResult.failure("WEBHOOK_ERROR", "Failed to send webhook");
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
