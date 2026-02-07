package com.aiinpocket.n3n.execution.handler.handlers.data;

import com.aiinpocket.n3n.execution.handler.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Handler for JWT (JSON Web Token) nodes.
 * Supports creating, verifying, and decoding JWT tokens
 * using HMAC-based algorithms (HS256, HS384, HS512).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtNodeHandler extends AbstractNodeHandler {

    private static final Map<String, String> ALGORITHM_MAP = Map.of(
        "HS256", "HmacSHA256",
        "HS384", "HmacSHA384",
        "HS512", "HmacSHA512"
    );

    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return "jwt";
    }

    @Override
    public String getDisplayName() {
        return "JWT";
    }

    @Override
    public String getDescription() {
        return "Create, verify, and decode JSON Web Tokens (JWT).";
    }

    @Override
    public String getCategory() {
        return "Tools";
    }

    @Override
    public String getIcon() {
        return "key";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String operation = getStringConfig(context, "operation", "sign");

        try {
            return switch (operation) {
                case "sign" -> signJwt(context);
                case "verify" -> verifyJwt(context);
                case "decode" -> decodeJwt(context);
                default -> NodeExecutionResult.failure("Unknown JWT operation: " + operation);
            };
        } catch (Exception e) {
            log.error("JWT operation '{}' failed: {}", operation, e.getMessage(), e);
            return NodeExecutionResult.failure("JWT operation failed: " + e.getMessage());
        }
    }

    /**
     * Create and sign a new JWT token.
     */
    @SuppressWarnings("unchecked")
    private NodeExecutionResult signJwt(NodeExecutionContext context) throws Exception {
        String algorithm = getStringConfig(context, "algorithm", "HS256");
        String secret = getStringConfig(context, "secret", "");

        if (secret.isEmpty()) {
            return NodeExecutionResult.failure("Secret key is required for signing JWT");
        }

        if (!ALGORITHM_MAP.containsKey(algorithm)) {
            return NodeExecutionResult.failure("Unsupported algorithm: " + algorithm
                + ". Supported: " + ALGORITHM_MAP.keySet());
        }

        // Build payload
        Map<String, Object> payload = getMapConfig(context, "payload");
        if (payload.isEmpty() && context.getInputData() != null) {
            Object inputPayload = context.getInputData().get("payload");
            if (inputPayload instanceof Map) {
                payload = new HashMap<>((Map<String, Object>) inputPayload);
            } else if (inputPayload instanceof String) {
                payload = objectMapper.readValue((String) inputPayload, Map.class);
            }
        }
        if (payload.isEmpty()) {
            payload = new HashMap<>();
        }

        // Standard claims
        String issuer = getStringConfig(context, "issuer", "");
        String subject = getStringConfig(context, "subject", "");
        String audience = getStringConfig(context, "audience", "");
        int expiresIn = getIntConfig(context, "expiresIn", 3600);  // default 1 hour
        boolean addIssuedAt = getBooleanConfig(context, "addIssuedAt", true);

        // Ensure we work on a mutable copy
        payload = new HashMap<>(payload);

        if (!issuer.isEmpty()) {
            payload.put("iss", issuer);
        }
        if (!subject.isEmpty()) {
            payload.put("sub", subject);
        }
        if (!audience.isEmpty()) {
            payload.put("aud", audience);
        }
        if (addIssuedAt) {
            payload.put("iat", Instant.now().getEpochSecond());
        }
        if (expiresIn > 0) {
            payload.put("exp", Instant.now().getEpochSecond() + expiresIn);
        }

        // Create JWT
        String token = createJwtToken(algorithm, payload, secret);

        Map<String, Object> output = new HashMap<>();
        output.put("token", token);
        output.put("algorithm", algorithm);
        output.put("payload", payload);
        if (expiresIn > 0) {
            output.put("expiresAt", Instant.now().plusSeconds(expiresIn).toString());
        }

        return NodeExecutionResult.success(output);
    }

    /**
     * Verify a JWT token's signature and check expiration.
     */
    private NodeExecutionResult verifyJwt(NodeExecutionContext context) throws Exception {
        String token = getTokenFromContext(context);
        String secret = getStringConfig(context, "secret", "");
        boolean checkExpiration = getBooleanConfig(context, "checkExpiration", true);

        if (token.isEmpty()) {
            return NodeExecutionResult.failure("JWT token is required");
        }
        if (secret.isEmpty()) {
            return NodeExecutionResult.failure("Secret key is required for verification");
        }

        // Decode parts
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return NodeExecutionResult.failure("Invalid JWT format: expected 3 parts, got " + parts.length);
        }

        String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String payloadJson = new String(Base64.getUrlDecoder().decode(padBase64(parts[1])), StandardCharsets.UTF_8);

        @SuppressWarnings("unchecked")
        Map<String, Object> header = objectMapper.readValue(headerJson, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);

        String algorithm = header.get("alg") != null ? header.get("alg").toString() : "HS256";

        if (!ALGORITHM_MAP.containsKey(algorithm)) {
            return NodeExecutionResult.failure("Unsupported algorithm in token: " + algorithm);
        }

        // Verify signature
        String signatureInput = parts[0] + "." + parts[1];
        String expectedSignature = computeSignature(signatureInput, secret, algorithm);
        boolean signatureValid = constantTimeEquals(parts[2], expectedSignature);

        // Check expiration
        boolean expired = false;
        if (checkExpiration && payload.containsKey("exp")) {
            long exp = ((Number) payload.get("exp")).longValue();
            expired = Instant.now().getEpochSecond() > exp;
        }

        // Check not-before
        boolean notYetValid = false;
        if (payload.containsKey("nbf")) {
            long nbf = ((Number) payload.get("nbf")).longValue();
            notYetValid = Instant.now().getEpochSecond() < nbf;
        }

        boolean valid = signatureValid && !expired && !notYetValid;

        Map<String, Object> output = new HashMap<>();
        output.put("valid", valid);
        output.put("signatureValid", signatureValid);
        output.put("expired", expired);
        output.put("notYetValid", notYetValid);
        output.put("header", header);
        output.put("payload", payload);
        output.put("algorithm", algorithm);

        if (!valid) {
            List<String> errors = new ArrayList<>();
            if (!signatureValid) errors.add("Invalid signature");
            if (expired) errors.add("Token has expired");
            if (notYetValid) errors.add("Token is not yet valid (nbf claim)");
            output.put("errors", errors);
        }

        return NodeExecutionResult.success(output);
    }

    /**
     * Decode a JWT token without verifying the signature.
     * Useful for inspecting token contents when the secret is not available.
     */
    private NodeExecutionResult decodeJwt(NodeExecutionContext context) throws Exception {
        String token = getTokenFromContext(context);

        if (token.isEmpty()) {
            return NodeExecutionResult.failure("JWT token is required");
        }

        String[] parts = token.split("\\.");
        if (parts.length < 2 || parts.length > 3) {
            return NodeExecutionResult.failure("Invalid JWT format: expected 2-3 parts, got " + parts.length);
        }

        String headerJson = new String(Base64.getUrlDecoder().decode(padBase64(parts[0])), StandardCharsets.UTF_8);
        String payloadJson = new String(Base64.getUrlDecoder().decode(padBase64(parts[1])), StandardCharsets.UTF_8);

        @SuppressWarnings("unchecked")
        Map<String, Object> header = objectMapper.readValue(headerJson, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);

        Map<String, Object> output = new HashMap<>();
        output.put("header", header);
        output.put("payload", payload);
        output.put("algorithm", header.get("alg"));
        output.put("hasSignature", parts.length == 3 && !parts[2].isEmpty());

        // Extract standard claims for convenience
        Map<String, Object> claims = new LinkedHashMap<>();
        if (payload.containsKey("iss")) claims.put("issuer", payload.get("iss"));
        if (payload.containsKey("sub")) claims.put("subject", payload.get("sub"));
        if (payload.containsKey("aud")) claims.put("audience", payload.get("aud"));
        if (payload.containsKey("iat")) {
            long iat = ((Number) payload.get("iat")).longValue();
            claims.put("issuedAt", Instant.ofEpochSecond(iat).toString());
        }
        if (payload.containsKey("exp")) {
            long exp = ((Number) payload.get("exp")).longValue();
            claims.put("expiresAt", Instant.ofEpochSecond(exp).toString());
            claims.put("expired", Instant.now().getEpochSecond() > exp);
        }
        if (payload.containsKey("nbf")) {
            long nbf = ((Number) payload.get("nbf")).longValue();
            claims.put("notBefore", Instant.ofEpochSecond(nbf).toString());
        }
        if (payload.containsKey("jti")) claims.put("tokenId", payload.get("jti"));

        if (!claims.isEmpty()) {
            output.put("standardClaims", claims);
        }

        return NodeExecutionResult.success(output);
    }

    /**
     * Create a complete JWT token string.
     */
    private String createJwtToken(String algorithm, Map<String, Object> payload, String secret) throws Exception {
        // Build header
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", algorithm);
        header.put("typ", "JWT");

        String headerBase64 = base64UrlEncode(objectMapper.writeValueAsBytes(header));
        String payloadBase64 = base64UrlEncode(objectMapper.writeValueAsBytes(payload));

        String signatureInput = headerBase64 + "." + payloadBase64;
        String signature = computeSignature(signatureInput, secret, algorithm);

        return signatureInput + "." + signature;
    }

    /**
     * Compute the HMAC signature for JWT.
     */
    private String computeSignature(String input, String secret, String algorithm) throws Exception {
        String hmacAlgorithm = ALGORITHM_MAP.get(algorithm);
        if (hmacAlgorithm == null) {
            throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        }

        Mac mac = Mac.getInstance(hmacAlgorithm);
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), hmacAlgorithm);
        mac.init(keySpec);

        byte[] signatureBytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        return base64UrlEncode(signatureBytes);
    }

    /**
     * Base64 URL-safe encoding without padding.
     */
    private String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    /**
     * Pad Base64 URL-safe encoded string if needed.
     */
    private String padBase64(String base64) {
        int padding = 4 - (base64.length() % 4);
        if (padding < 4) {
            return base64 + "=".repeat(padding);
        }
        return base64;
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);

        if (aBytes.length != bBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < aBytes.length; i++) {
            result |= aBytes[i] ^ bBytes[i];
        }
        return result == 0;
    }

    /**
     * Get the JWT token from config or input data.
     */
    private String getTokenFromContext(NodeExecutionContext context) {
        String token = getStringConfig(context, "token", "");

        if (token.isEmpty() && context.getInputData() != null) {
            Object tokenData = context.getInputData().get("token");
            if (tokenData != null) {
                token = tokenData.toString();
            }
            // Also try "data" key
            if (token.isEmpty()) {
                Object data = context.getInputData().get("data");
                if (data != null) {
                    token = data.toString();
                }
            }
        }

        // Strip "Bearer " prefix if present
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        return token.trim();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("operation", Map.of(
            "type", "string",
            "title", "Operation",
            "description", "JWT operation to perform",
            "enum", List.of("sign", "verify", "decode"),
            "enumNames", List.of(
                "Sign (create a new JWT)",
                "Verify (validate JWT signature and claims)",
                "Decode (decode without verification)"
            ),
            "default", "sign"
        ));

        properties.put("algorithm", Map.of(
            "type", "string",
            "title", "Algorithm",
            "description", "HMAC algorithm for signing",
            "enum", List.of("HS256", "HS384", "HS512"),
            "default", "HS256"
        ));

        properties.put("secret", Map.of(
            "type", "string",
            "title", "Secret Key",
            "description", "Secret key for HMAC signing/verification",
            "format", "password"
        ));

        properties.put("token", Map.of(
            "type", "string",
            "title", "JWT Token",
            "description", "JWT token to verify or decode (for 'verify' and 'decode' operations)"
        ));

        properties.put("payload", Map.of(
            "type", "object",
            "title", "Payload",
            "description", "JWT payload claims (for 'sign' operation)"
        ));

        properties.put("issuer", Map.of(
            "type", "string",
            "title", "Issuer (iss)",
            "description", "Token issuer claim"
        ));

        properties.put("subject", Map.of(
            "type", "string",
            "title", "Subject (sub)",
            "description", "Token subject claim"
        ));

        properties.put("audience", Map.of(
            "type", "string",
            "title", "Audience (aud)",
            "description", "Token audience claim"
        ));

        properties.put("expiresIn", Map.of(
            "type", "integer",
            "title", "Expires In (seconds)",
            "description", "Token expiration time in seconds from now (0 = no expiration)",
            "default", 3600,
            "minimum", 0
        ));

        properties.put("addIssuedAt", Map.of(
            "type", "boolean",
            "title", "Add Issued At (iat)",
            "description", "Add current timestamp as issued-at claim",
            "default", true
        ));

        properties.put("checkExpiration", Map.of(
            "type", "boolean",
            "title", "Check Expiration",
            "description", "Verify token expiration during validation",
            "default", true
        ));

        return Map.of(
            "type", "object",
            "properties", properties
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "object")
            )
        );
    }
}
