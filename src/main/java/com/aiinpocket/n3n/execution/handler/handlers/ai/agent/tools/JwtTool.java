package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * JWT parsing tool
 * Parse and inspect JWT tokens (parsing only, not generation)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTool implements AgentNodeTool {

    private final ObjectMapper objectMapper;

    @Override
    public String getId() {
        return "jwt";
    }

    @Override
    public String getName() {
        return "JWT Parser";
    }

    @Override
    public String getDescription() {
        return """
                JWT (JSON Web Token) parsing tool.

                Features:
                - Parse JWT Header and Payload
                - Display expiration and issued-at times
                - Check if token is expired

                Parameters:
                - token: JWT string

                Note: This tool can only parse JWTs, it cannot verify signatures or generate new JWTs.
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "token", Map.of(
                                "type", "string",
                                "description", "JWT Token"
                        )
                ),
                "required", List.of("token")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String token = (String) parameters.get("token");

                if (token == null || token.isBlank()) {
                    return ToolResult.failure("Token cannot be empty");
                }

                // Security: limit token size
                if (token.length() > 10_000) {
                    return ToolResult.failure("Token too long");
                }

                // Remove Bearer prefix if present
                if (token.toLowerCase().startsWith("bearer ")) {
                    token = token.substring(7);
                }

                // Split JWT
                String[] parts = token.split("\\.");
                if (parts.length != 3) {
                    return ToolResult.failure("Invalid JWT format, should have 3 parts separated by dots");
                }

                // Decode header and payload
                Map<String, Object> header = decodeBase64Json(parts[0]);
                Map<String, Object> payload = decodeBase64Json(parts[1]);

                if (header == null || payload == null) {
                    return ToolResult.failure("Cannot parse JWT");
                }

                // Check expiration
                boolean isExpired = false;
                String expiration = null;
                String issuedAt = null;
                String notBefore = null;

                DateTimeFormatter formatter = DateTimeFormatter
                        .ofPattern("yyyy-MM-dd HH:mm:ss z")
                        .withZone(ZoneId.systemDefault());

                if (payload.containsKey("exp")) {
                    long exp = ((Number) payload.get("exp")).longValue();
                    expiration = formatter.format(Instant.ofEpochSecond(exp));
                    isExpired = Instant.now().isAfter(Instant.ofEpochSecond(exp));
                }

                if (payload.containsKey("iat")) {
                    long iat = ((Number) payload.get("iat")).longValue();
                    issuedAt = formatter.format(Instant.ofEpochSecond(iat));
                }

                if (payload.containsKey("nbf")) {
                    long nbf = ((Number) payload.get("nbf")).longValue();
                    notBefore = formatter.format(Instant.ofEpochSecond(nbf));
                }

                StringBuilder sb = new StringBuilder();
                sb.append("JWT parse result:\n\n");

                sb.append("=== Header ===\n");
                sb.append(String.format("- Algorithm (alg): %s\n", header.get("alg")));
                sb.append(String.format("- Type (typ): %s\n", header.getOrDefault("typ", "JWT")));
                if (header.containsKey("kid")) {
                    sb.append(String.format("- Key ID (kid): %s\n", header.get("kid")));
                }

                sb.append("\n=== Payload ===\n");
                if (payload.containsKey("sub")) {
                    sb.append(String.format("- Subject (sub): %s\n", payload.get("sub")));
                }
                if (payload.containsKey("iss")) {
                    sb.append(String.format("- Issuer (iss): %s\n", payload.get("iss")));
                }
                if (payload.containsKey("aud")) {
                    sb.append(String.format("- Audience (aud): %s\n", payload.get("aud")));
                }
                if (issuedAt != null) {
                    sb.append(String.format("- Issued at (iat): %s\n", issuedAt));
                }
                if (expiration != null) {
                    sb.append(String.format("- Expiration (exp): %s\n", expiration));
                }
                if (notBefore != null) {
                    sb.append(String.format("- Not before (nbf): %s\n", notBefore));
                }
                if (payload.containsKey("jti")) {
                    sb.append(String.format("- Token ID (jti): %s\n", payload.get("jti")));
                }

                // Custom claims
                Set<String> standardClaims = Set.of("sub", "iss", "aud", "exp", "iat", "nbf", "jti");
                sb.append("\n=== Custom Claims ===\n");
                boolean hasCustomClaims = false;
                for (Map.Entry<String, Object> entry : payload.entrySet()) {
                    if (!standardClaims.contains(entry.getKey())) {
                        sb.append(String.format("- %s: %s\n", entry.getKey(), entry.getValue()));
                        hasCustomClaims = true;
                    }
                }
                if (!hasCustomClaims) {
                    sb.append("(none)\n");
                }

                sb.append("\n=== Status ===\n");
                if (isExpired) {
                    sb.append("WARNING: Token is expired!\n");
                } else if (expiration != null) {
                    sb.append("OK: Token is not expired\n");
                } else {
                    sb.append("WARNING: Token has no expiration time set\n");
                }

                return ToolResult.success(sb.toString(), Map.of(
                        "header", header,
                        "payload", payload,
                        "isExpired", isExpired,
                        "expiration", expiration != null ? expiration : "",
                        "issuedAt", issuedAt != null ? issuedAt : ""
                ));

            } catch (Exception e) {
                log.error("JWT parsing failed", e);
                return ToolResult.failure("JWT parsing failed");
            }
        });
    }

    private Map<String, Object> decodeBase64Json(String base64) {
        try {
            // Handle URL-safe base64
            String normalized = base64
                    .replace('-', '+')
                    .replace('_', '/');

            // Add padding if needed
            int padding = (4 - normalized.length() % 4) % 4;
            normalized = normalized + "=".repeat(padding);

            byte[] decoded = Base64.getDecoder().decode(normalized);
            String json = new String(decoded, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Failed to decode base64 JSON: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String getCategory() {
        return "security";
    }
}
