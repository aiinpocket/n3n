package com.aiinpocket.n3n.execution.handler.handlers.data;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtNodeHandlerTest {

    private JwtNodeHandler handler;
    private ObjectMapper objectMapper;

    private static final String TEST_SECRET = "my-super-secret-key-for-testing-purposes-1234";

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new JwtNodeHandler(objectMapper);
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsJwt() {
        assertThat(handler.getType()).isEqualTo("jwt");
    }

    @Test
    void getCategory_returnsTools() {
        assertThat(handler.getCategory()).isEqualTo("Tools");
    }

    @Test
    void getDisplayName_returnsJWT() {
        assertThat(handler.getDisplayName()).isEqualTo("JWT");
    }

    // ========== Sign: HS256 ==========

    @Test
    void execute_signHS256_returnsValidToken() {
        NodeExecutionResult result = executeSign("HS256", TEST_SECRET, Map.of("user", "alice"), Map.of());

        assertThat(result.isSuccess()).isTrue();
        String token = (String) result.getOutput().get("token");
        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(result.getOutput()).containsEntry("algorithm", "HS256");
    }

    // ========== Sign: HS384 ==========

    @Test
    void execute_signHS384_returnsValidToken() {
        NodeExecutionResult result = executeSign("HS384", TEST_SECRET, Map.of("user", "bob"), Map.of());

        assertThat(result.isSuccess()).isTrue();
        String token = (String) result.getOutput().get("token");
        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(result.getOutput()).containsEntry("algorithm", "HS384");
    }

    // ========== Sign: HS512 ==========

    @Test
    void execute_signHS512_returnsValidToken() {
        NodeExecutionResult result = executeSign("HS512", TEST_SECRET, Map.of("user", "charlie"), Map.of());

        assertThat(result.isSuccess()).isTrue();
        String token = (String) result.getOutput().get("token");
        assertThat(token).isNotNull();
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(result.getOutput()).containsEntry("algorithm", "HS512");
    }

    // ========== Sign: Custom Claims ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_signWithCustomClaims_claimsIncludedInPayload() {
        Map<String, Object> extraConfig = new HashMap<>();
        extraConfig.put("issuer", "n3n-test");
        extraConfig.put("subject", "user-123");
        extraConfig.put("audience", "api-service");

        NodeExecutionResult result = executeSign("HS256", TEST_SECRET, Map.of(), extraConfig);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> payload = (Map<String, Object>) result.getOutput().get("payload");
        assertThat(payload).containsEntry("iss", "n3n-test");
        assertThat(payload).containsEntry("sub", "user-123");
        assertThat(payload).containsEntry("aud", "api-service");
    }

    // ========== Sign: Expiration ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_signWithExpiration_expiresAtSet() {
        Map<String, Object> extraConfig = new HashMap<>();
        extraConfig.put("expiresIn", 7200);

        NodeExecutionResult result = executeSign("HS256", TEST_SECRET, Map.of(), extraConfig);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> payload = (Map<String, Object>) result.getOutput().get("payload");
        assertThat(payload).containsKey("exp");
        assertThat(payload).containsKey("iat");
        assertThat(result.getOutput()).containsKey("expiresAt");
    }

    @Test
    @SuppressWarnings("unchecked")
    void execute_signWithZeroExpiration_noExpClaim() {
        Map<String, Object> extraConfig = new HashMap<>();
        extraConfig.put("expiresIn", 0);

        NodeExecutionResult result = executeSign("HS256", TEST_SECRET, Map.of(), extraConfig);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> payload = (Map<String, Object>) result.getOutput().get("payload");
        assertThat(payload).doesNotContainKey("exp");
    }

    // ========== Sign: Empty Secret ==========

    @Test
    void execute_signWithEmptySecret_returnsFailure() {
        NodeExecutionResult result = executeSign("HS256", "", Map.of("data", "test"), Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Secret key is required");
    }

    // ========== Sign: Invalid Algorithm ==========

    @Test
    void execute_signWithInvalidAlgorithm_returnsFailure() {
        NodeExecutionResult result = executeSign("RS256", TEST_SECRET, Map.of(), Map.of());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Unsupported algorithm");
    }

    // ========== Verify: Valid Token ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_verifyValidToken_returnsValid() {
        // First sign a token
        NodeExecutionResult signResult = executeSign("HS256", TEST_SECRET, Map.of("role", "admin"), Map.of());
        String token = (String) signResult.getOutput().get("token");

        // Then verify it
        NodeExecutionResult verifyResult = executeVerify(token, TEST_SECRET);

        assertThat(verifyResult.isSuccess()).isTrue();
        assertThat(verifyResult.getOutput()).containsEntry("valid", true);
        assertThat(verifyResult.getOutput()).containsEntry("signatureValid", true);
        assertThat(verifyResult.getOutput()).containsEntry("expired", false);

        Map<String, Object> payload = (Map<String, Object>) verifyResult.getOutput().get("payload");
        assertThat(payload).containsEntry("role", "admin");
    }

    // ========== Verify: Invalid Signature ==========

    @Test
    void execute_verifyInvalidSignature_returnsInvalid() {
        // Sign with one secret
        NodeExecutionResult signResult = executeSign("HS256", TEST_SECRET, Map.of(), Map.of());
        String token = (String) signResult.getOutput().get("token");

        // Verify with different secret
        NodeExecutionResult verifyResult = executeVerify(token, "wrong-secret");

        assertThat(verifyResult.isSuccess()).isTrue();
        assertThat(verifyResult.getOutput()).containsEntry("valid", false);
        assertThat(verifyResult.getOutput()).containsEntry("signatureValid", false);
    }

    // ========== Verify: Expired Token ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_verifyExpiredToken_returnsExpired() throws Exception {
        // Create a token that is already expired
        // We'll craft a JWT manually with exp in the past
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new HashMap<>();
        payload.put("exp", Instant.now().getEpochSecond() - 3600); // expired 1 hour ago
        payload.put("data", "test");

        String headerB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(header));
        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(payload));

        String signatureInput = headerB64 + "." + payloadB64;

        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(signatureInput.getBytes(StandardCharsets.UTF_8)));

        String token = signatureInput + "." + signature;

        NodeExecutionResult verifyResult = executeVerify(token, TEST_SECRET);

        assertThat(verifyResult.isSuccess()).isTrue();
        assertThat(verifyResult.getOutput()).containsEntry("signatureValid", true);
        assertThat(verifyResult.getOutput()).containsEntry("expired", true);
        assertThat(verifyResult.getOutput()).containsEntry("valid", false);

        List<String> errors = (List<String>) verifyResult.getOutput().get("errors");
        assertThat(errors).contains("Token has expired");
    }

    // ========== Verify: Not-Before Claim ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_verifyTokenWithFutureNbf_returnsNotYetValid() throws Exception {
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> payload = new HashMap<>();
        payload.put("nbf", Instant.now().getEpochSecond() + 3600); // valid 1 hour from now
        payload.put("exp", Instant.now().getEpochSecond() + 7200);
        payload.put("data", "test");

        String headerB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(header));
        String payloadB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(payload));

        String signatureInput = headerB64 + "." + payloadB64;

        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(signatureInput.getBytes(StandardCharsets.UTF_8)));

        String token = signatureInput + "." + signature;

        NodeExecutionResult verifyResult = executeVerify(token, TEST_SECRET);

        assertThat(verifyResult.isSuccess()).isTrue();
        assertThat(verifyResult.getOutput()).containsEntry("notYetValid", true);
        assertThat(verifyResult.getOutput()).containsEntry("valid", false);

        List<String> errors = (List<String>) verifyResult.getOutput().get("errors");
        assertThat(errors).contains("Token is not yet valid (nbf claim)");
    }

    // ========== Verify: Empty Token ==========

    @Test
    void execute_verifyEmptyToken_returnsFailure() {
        NodeExecutionResult result = executeVerify("", TEST_SECRET);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("JWT token is required");
    }

    // ========== Verify: Empty Secret ==========

    @Test
    void execute_verifyEmptySecret_returnsFailure() {
        NodeExecutionResult signResult = executeSign("HS256", TEST_SECRET, Map.of(), Map.of());
        String token = (String) signResult.getOutput().get("token");

        Map<String, Object> config = new HashMap<>();
        config.put("operation", "verify");
        config.put("token", token);
        config.put("secret", "");

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("jwt-1")
                .nodeType("jwt")
                .nodeConfig(new HashMap<>(config))
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Secret key is required");
    }

    // ========== Decode: Without Verification ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_decodeToken_returnsHeaderAndPayload() {
        NodeExecutionResult signResult = executeSign("HS256", TEST_SECRET,
                Map.of("user", "alice", "role", "admin"), Map.of());
        String token = (String) signResult.getOutput().get("token");

        NodeExecutionResult decodeResult = executeDecode(token);

        assertThat(decodeResult.isSuccess()).isTrue();
        assertThat(decodeResult.getOutput()).containsKey("header");
        assertThat(decodeResult.getOutput()).containsKey("payload");

        Map<String, Object> header = (Map<String, Object>) decodeResult.getOutput().get("header");
        assertThat(header).containsEntry("alg", "HS256");
        assertThat(header).containsEntry("typ", "JWT");

        Map<String, Object> payload = (Map<String, Object>) decodeResult.getOutput().get("payload");
        assertThat(payload).containsEntry("user", "alice");
        assertThat(payload).containsEntry("role", "admin");
    }

    @Test
    void execute_decodeToken_showsHasSignature() {
        NodeExecutionResult signResult = executeSign("HS256", TEST_SECRET, Map.of(), Map.of());
        String token = (String) signResult.getOutput().get("token");

        NodeExecutionResult decodeResult = executeDecode(token);

        assertThat(decodeResult.isSuccess()).isTrue();
        assertThat(decodeResult.getOutput()).containsEntry("hasSignature", true);
        assertThat(decodeResult.getOutput()).containsEntry("algorithm", "HS256");
    }

    // ========== Decode: Standard Claims Extraction ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_decodeWithStandardClaims_extractedForConvenience() {
        Map<String, Object> extraConfig = new HashMap<>();
        extraConfig.put("issuer", "n3n");
        extraConfig.put("subject", "user-1");

        NodeExecutionResult signResult = executeSign("HS256", TEST_SECRET, Map.of(), extraConfig);
        String token = (String) signResult.getOutput().get("token");

        NodeExecutionResult decodeResult = executeDecode(token);

        assertThat(decodeResult.isSuccess()).isTrue();
        Map<String, Object> standardClaims = (Map<String, Object>) decodeResult.getOutput().get("standardClaims");
        assertThat(standardClaims).isNotNull();
        assertThat(standardClaims).containsEntry("issuer", "n3n");
        assertThat(standardClaims).containsEntry("subject", "user-1");
        assertThat(standardClaims).containsKey("issuedAt");
        assertThat(standardClaims).containsKey("expiresAt");
    }

    // ========== Bearer Prefix Stripping ==========

    @Test
    void execute_verifyBearerPrefixToken_stripsPrefix() {
        NodeExecutionResult signResult = executeSign("HS256", TEST_SECRET, Map.of("data", "test"), Map.of());
        String token = (String) signResult.getOutput().get("token");
        String bearerToken = "Bearer " + token;

        NodeExecutionResult verifyResult = executeVerify(bearerToken, TEST_SECRET);

        assertThat(verifyResult.isSuccess()).isTrue();
        assertThat(verifyResult.getOutput()).containsEntry("valid", true);
    }

    // ========== Invalid Token Format ==========

    @Test
    void execute_verifyInvalidTokenFormat_returnsFailure() {
        NodeExecutionResult result = executeVerify("not.a.valid.jwt.token", TEST_SECRET);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void execute_verifyTokenWithOnlyOnePart_returnsFailure() {
        NodeExecutionResult result = executeVerify("singlepart", TEST_SECRET);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Invalid JWT format");
    }

    // ========== Decode: Empty Token ==========

    @Test
    void execute_decodeEmptyToken_returnsFailure() {
        NodeExecutionResult result = executeDecode("");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("JWT token is required");
    }

    // ========== Unknown Operation ==========

    @Test
    void execute_unknownOperation_returnsFailure() {
        Map<String, Object> config = new HashMap<>();
        config.put("operation", "invalid");
        config.put("secret", TEST_SECRET);

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("jwt-1")
                .nodeType("jwt")
                .nodeConfig(new HashMap<>(config))
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Unknown JWT operation");
    }

    // ========== Roundtrip: Sign and Verify ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_signThenVerify_roundtripSucceeds() {
        Map<String, Object> payload = Map.of("userId", "u-123", "permissions", List.of("read", "write"));

        NodeExecutionResult signResult = executeSign("HS256", TEST_SECRET, payload, Map.of());
        assertThat(signResult.isSuccess()).isTrue();
        String token = (String) signResult.getOutput().get("token");

        NodeExecutionResult verifyResult = executeVerify(token, TEST_SECRET);
        assertThat(verifyResult.isSuccess()).isTrue();
        assertThat(verifyResult.getOutput()).containsEntry("valid", true);

        Map<String, Object> verifiedPayload = (Map<String, Object>) verifyResult.getOutput().get("payload");
        assertThat(verifiedPayload).containsEntry("userId", "u-123");
    }

    // ========== Custom Payload ==========

    @Test
    @SuppressWarnings("unchecked")
    void execute_signWithCustomPayload_allClaimsPreserved() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("customClaim1", "value1");
        payload.put("customClaim2", 42);
        payload.put("nested", Map.of("key", "val"));

        NodeExecutionResult result = executeSign("HS256", TEST_SECRET, payload, Map.of());

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> outputPayload = (Map<String, Object>) result.getOutput().get("payload");
        assertThat(outputPayload).containsEntry("customClaim1", "value1");
        assertThat(outputPayload).containsEntry("customClaim2", 42);
        assertThat(outputPayload).containsKey("nested");
    }

    // ========== Token from Input Data ==========

    @Test
    void execute_verifyTokenFromInputData_worksCorrectly() {
        NodeExecutionResult signResult = executeSign("HS256", TEST_SECRET, Map.of("x", 1), Map.of());
        String token = (String) signResult.getOutput().get("token");

        Map<String, Object> config = new HashMap<>();
        config.put("operation", "verify");
        config.put("secret", TEST_SECRET);

        Map<String, Object> inputData = new HashMap<>();
        inputData.put("token", token);

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("jwt-1")
                .nodeType("jwt")
                .nodeConfig(new HashMap<>(config))
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("valid", true);
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_containsExpectedProperties() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("operation");
        assertThat(properties).containsKey("algorithm");
        assertThat(properties).containsKey("secret");
        assertThat(properties).containsKey("token");
        assertThat(properties).containsKey("payload");
        assertThat(properties).containsKey("issuer");
        assertThat(properties).containsKey("subject");
        assertThat(properties).containsKey("audience");
        assertThat(properties).containsKey("expiresIn");
    }

    @Test
    void getInterfaceDefinition_hasInputsAndOutputs() {
        var iface = handler.getInterfaceDefinition();
        assertThat(iface).containsKey("inputs");
        assertThat(iface).containsKey("outputs");
    }

    // ========== Helpers ==========

    private NodeExecutionResult executeSign(String algorithm, String secret,
                                            Map<String, Object> payload,
                                            Map<String, Object> extraConfig) {
        Map<String, Object> config = new HashMap<>();
        config.put("operation", "sign");
        config.put("algorithm", algorithm);
        config.put("secret", secret);
        if (!payload.isEmpty()) {
            config.put("payload", new HashMap<>(payload));
        }
        config.putAll(extraConfig);

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("jwt-1")
                .nodeType("jwt")
                .nodeConfig(new HashMap<>(config))
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();

        return handler.execute(context);
    }

    private NodeExecutionResult executeVerify(String token, String secret) {
        Map<String, Object> config = new HashMap<>();
        config.put("operation", "verify");
        config.put("token", token);
        config.put("secret", secret);

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("jwt-1")
                .nodeType("jwt")
                .nodeConfig(new HashMap<>(config))
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();

        return handler.execute(context);
    }

    private NodeExecutionResult executeDecode(String token) {
        Map<String, Object> config = new HashMap<>();
        config.put("operation", "decode");
        config.put("token", token);

        NodeExecutionContext context = NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("jwt-1")
                .nodeType("jwt")
                .nodeConfig(new HashMap<>(config))
                .inputData(Map.of())
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();

        return handler.execute(context);
    }
}
