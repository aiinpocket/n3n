package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CryptoNodeHandlerTest {

    private CryptoNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new CryptoNodeHandler();
    }

    // ========== Basic Properties ==========

    @Test
    void getType_returnsCrypto() {
        assertThat(handler.getType()).isEqualTo("crypto");
    }

    @Test
    void getCategory_returnsTools() {
        assertThat(handler.getCategory()).isEqualTo("Tools");
    }

    @Test
    void getDisplayName_returnsCrypto() {
        assertThat(handler.getDisplayName()).isEqualTo("Crypto");
    }

    // ========== Hash SHA-256 ==========

    @Test
    void execute_hashSHA256_producesNonEmptyHexString() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "hash", "algorithm", "SHA-256", "input", "hello world")),
                Map.of()
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String hash = (String) result.getOutput().get("hash");
        assertThat(hash).isNotEmpty();
        // SHA-256 hex is 64 characters
        assertThat(hash).hasSize(64);
        // Only hex characters
        assertThat(hash).matches("[0-9a-f]+");
        assertThat(result.getOutput()).containsEntry("algorithm", "SHA-256");
    }

    @Test
    void execute_hashSHA256_sameInputProducesSameHash() {
        NodeExecutionContext context1 = buildContext(
                new HashMap<>(Map.of("operation", "hash", "algorithm", "SHA-256", "input", "test data")),
                Map.of()
        );
        NodeExecutionContext context2 = buildContext(
                new HashMap<>(Map.of("operation", "hash", "algorithm", "SHA-256", "input", "test data")),
                Map.of()
        );

        NodeExecutionResult result1 = handler.execute(context1);
        NodeExecutionResult result2 = handler.execute(context2);

        assertThat(result1.isSuccess()).isTrue();
        assertThat(result2.isSuccess()).isTrue();
        assertThat(result1.getOutput().get("hash")).isEqualTo(result2.getOutput().get("hash"));
    }

    @Test
    void execute_hashSHA256_differentInputProducesDifferentHash() {
        NodeExecutionContext context1 = buildContext(
                new HashMap<>(Map.of("operation", "hash", "algorithm", "SHA-256", "input", "input1")),
                Map.of()
        );
        NodeExecutionContext context2 = buildContext(
                new HashMap<>(Map.of("operation", "hash", "algorithm", "SHA-256", "input", "input2")),
                Map.of()
        );

        NodeExecutionResult result1 = handler.execute(context1);
        NodeExecutionResult result2 = handler.execute(context2);

        assertThat(result1.getOutput().get("hash")).isNotEqualTo(result2.getOutput().get("hash"));
    }

    // ========== Hash with Base64 Encoding ==========

    @Test
    void execute_hashBase64Encoding_producesBase64String() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "hash", "algorithm", "SHA-256", "encoding", "base64", "input", "hello")),
                Map.of()
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String hash = (String) result.getOutput().get("hash");
        assertThat(hash).isNotEmpty();
        // Base64 decoding should not throw
        assertThatCode(() -> Base64.getDecoder().decode(hash)).doesNotThrowAnyException();
    }

    // ========== Hash SHA-512 ==========

    @Test
    void execute_hashSHA512_produces128CharHex() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "hash", "algorithm", "SHA-512", "input", "test")),
                Map.of()
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String hash = (String) result.getOutput().get("hash");
        // SHA-512 hex is 128 characters
        assertThat(hash).hasSize(128);
        assertThat(result.getOutput()).containsEntry("algorithm", "SHA-512");
    }

    // ========== HMAC ==========

    @Test
    void execute_hmac_producesNonEmptyResult() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "hmac", "algorithm", "HmacSHA256", "key", "secret-key", "input", "message")),
                Map.of()
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String hmac = (String) result.getOutput().get("hmac");
        assertThat(hmac).isNotEmpty();
        assertThat(hmac).matches("[0-9a-f]+");
        assertThat(result.getOutput()).containsEntry("algorithm", "HmacSHA256");
    }

    @Test
    void execute_hmac_sameInputAndKey_producesSameResult() {
        NodeExecutionContext context1 = buildContext(
                new HashMap<>(Map.of("operation", "hmac", "algorithm", "HmacSHA256", "key", "mykey", "input", "data")),
                Map.of()
        );
        NodeExecutionContext context2 = buildContext(
                new HashMap<>(Map.of("operation", "hmac", "algorithm", "HmacSHA256", "key", "mykey", "input", "data")),
                Map.of()
        );

        NodeExecutionResult result1 = handler.execute(context1);
        NodeExecutionResult result2 = handler.execute(context2);

        assertThat(result1.getOutput().get("hmac")).isEqualTo(result2.getOutput().get("hmac"));
    }

    // ========== Encrypt / Decrypt Roundtrip ==========

    @Test
    void execute_encryptThenDecrypt_roundtrip() {
        String plaintext = "This is a secret message!";
        String key = "my-encryption-key-12345";

        // Encrypt
        NodeExecutionContext encryptContext = buildContext(
                new HashMap<>(Map.of("operation", "encrypt", "key", key, "input", plaintext)),
                Map.of()
        );
        NodeExecutionResult encryptResult = handler.execute(encryptContext);

        assertThat(encryptResult.isSuccess()).isTrue();
        String encrypted = (String) encryptResult.getOutput().get("encrypted");
        assertThat(encrypted).isNotEmpty();
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(encryptResult.getOutput()).containsEntry("algorithm", "AES-GCM");

        // Decrypt
        NodeExecutionContext decryptContext = buildContext(
                new HashMap<>(Map.of("operation", "decrypt", "key", key, "ciphertext", encrypted)),
                Map.of()
        );
        NodeExecutionResult decryptResult = handler.execute(decryptContext);

        assertThat(decryptResult.isSuccess()).isTrue();
        assertThat(decryptResult.getOutput().get("decrypted")).isEqualTo(plaintext);
    }

    // ========== Encrypt without Key ==========

    @Test
    void execute_encryptWithoutKey_returnsFailure() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "encrypt", "input", "secret")),
                Map.of()
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Encryption key is required");
    }

    // ========== Decrypt without Key ==========

    @Test
    void execute_decryptWithoutKey_returnsFailure() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "decrypt", "ciphertext", "somedata")),
                Map.of()
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Decryption key is required");
    }

    // ========== Base64 Encode / Decode Roundtrip ==========

    @Test
    void execute_base64Encode_producesEncodedString() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "base64Encode", "input", "Hello World")),
                Map.of()
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String encoded = (String) result.getOutput().get("encoded");
        assertThat(encoded).isEqualTo("SGVsbG8gV29ybGQ=");
    }

    @Test
    void execute_base64Decode_decodesCorrectly() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "base64Decode", "input", "SGVsbG8gV29ybGQ=")),
                Map.of()
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("decoded")).isEqualTo("Hello World");
    }

    @Test
    void execute_base64EncodeDecode_roundtrip() {
        String original = "Test data with special chars: !@#$%^&*()";

        // Encode
        NodeExecutionContext encodeContext = buildContext(
                new HashMap<>(Map.of("operation", "base64Encode", "input", original)),
                Map.of()
        );
        NodeExecutionResult encodeResult = handler.execute(encodeContext);
        assertThat(encodeResult.isSuccess()).isTrue();
        String encoded = (String) encodeResult.getOutput().get("encoded");

        // Decode
        NodeExecutionContext decodeContext = buildContext(
                new HashMap<>(Map.of("operation", "base64Decode", "input", encoded)),
                Map.of()
        );
        NodeExecutionResult decodeResult = handler.execute(decodeContext);
        assertThat(decodeResult.isSuccess()).isTrue();
        assertThat(decodeResult.getOutput().get("decoded")).isEqualTo(original);
    }

    // ========== Random ==========

    @Test
    void execute_random_producesNonEmptyHex() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "random", "length", 16)),
                Map.of()
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String random = (String) result.getOutput().get("random");
        assertThat(random).isNotEmpty();
        // 16 bytes = 32 hex chars
        assertThat(random).hasSize(32);
        assertThat(random).matches("[0-9a-f]+");
        assertThat(result.getOutput()).containsEntry("length", 16);
    }

    @Test
    void execute_random_twoCalls_produceDifferentResults() {
        NodeExecutionContext context1 = buildContext(
                new HashMap<>(Map.of("operation", "random", "length", 32)),
                Map.of()
        );
        NodeExecutionContext context2 = buildContext(
                new HashMap<>(Map.of("operation", "random", "length", 32)),
                Map.of()
        );

        NodeExecutionResult result1 = handler.execute(context1);
        NodeExecutionResult result2 = handler.execute(context2);

        assertThat(result1.isSuccess()).isTrue();
        assertThat(result2.isSuccess()).isTrue();
        // Extremely unlikely to be the same
        assertThat(result1.getOutput().get("random")).isNotEqualTo(result2.getOutput().get("random"));
    }

    // ========== UUID ==========

    @Test
    void execute_uuid_producesValidUUIDFormat() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "uuid")),
                Map.of()
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String uuid = (String) result.getOutput().get("uuid");
        assertThat(uuid).isNotEmpty();
        // Validate UUID format: 8-4-4-4-12
        assertThat(uuid).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        // Should parse without exception
        assertThatCode(() -> UUID.fromString(uuid)).doesNotThrowAnyException();
    }

    @Test
    void execute_uuid_twoCalls_produceDifferentUUIDs() {
        NodeExecutionContext context1 = buildContext(
                new HashMap<>(Map.of("operation", "uuid")),
                Map.of()
        );
        NodeExecutionContext context2 = buildContext(
                new HashMap<>(Map.of("operation", "uuid")),
                Map.of()
        );

        NodeExecutionResult result1 = handler.execute(context1);
        NodeExecutionResult result2 = handler.execute(context2);

        assertThat(result1.getOutput().get("uuid")).isNotEqualTo(result2.getOutput().get("uuid"));
    }

    // ========== Unknown Operation ==========

    @Test
    void execute_unknownOperation_returnsFailure() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "nonExistentOp", "input", "data")),
                Map.of()
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Unknown crypto operation");
    }

    // ========== Input from inputData "data" key ==========

    @Test
    void execute_inputFromInputData_whenConfigInputEmpty() {
        // No "input" in config, but "data" in inputData
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "hash", "algorithm", "SHA-256")),
                Map.of("data", "hello from inputData")
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String hash = (String) result.getOutput().get("hash");
        assertThat(hash).isNotEmpty();
        assertThat(hash).hasSize(64);
    }

    @Test
    void execute_configInputTakesPrecedence_overInputData() {
        // Both config "input" and inputData "data" present
        NodeExecutionContext contextFromConfig = buildContext(
                new HashMap<>(Map.of("operation", "hash", "algorithm", "SHA-256", "input", "from config")),
                Map.of("data", "from inputData")
        );

        NodeExecutionContext contextFromData = buildContext(
                new HashMap<>(Map.of("operation", "hash", "algorithm", "SHA-256")),
                Map.of("data", "from config")
        );

        NodeExecutionResult resultConfig = handler.execute(contextFromConfig);
        NodeExecutionResult resultData = handler.execute(contextFromData);

        // Both should hash "from config" and produce the same hash
        assertThat(resultConfig.getOutput().get("hash")).isEqualTo(resultData.getOutput().get("hash"));
    }

    // ========== Hash MD5 ==========

    @Test
    void execute_hashMD5_produces32CharHex() {
        NodeExecutionContext context = buildContext(
                new HashMap<>(Map.of("operation", "hash", "algorithm", "MD5", "input", "test")),
                Map.of()
        );

        NodeExecutionResult result = handler.execute(context);

        assertThat(result.isSuccess()).isTrue();
        String hash = (String) result.getOutput().get("hash");
        // MD5 hex is 32 characters
        assertThat(hash).hasSize(32);
        assertThat(result.getOutput()).containsEntry("algorithm", "MD5");
    }

    // ========== Config Schema ==========

    @Test
    void getConfigSchema_containsExpectedProperties() {
        var schema = handler.getConfigSchema();
        assertThat(schema).containsKey("properties");
        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKey("operation");
        assertThat(properties).containsKey("input");
        assertThat(properties).containsKey("algorithm");
        assertThat(properties).containsKey("key");
        assertThat(properties).containsKey("encoding");
        assertThat(properties).containsKey("length");
    }

    @Test
    void getInterfaceDefinition_hasInputsAndOutputs() {
        var iface = handler.getInterfaceDefinition();
        assertThat(iface).containsKey("inputs");
        assertThat(iface).containsKey("outputs");
    }

    // ========== Helper ==========

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("crypto-1")
                .nodeType("crypto")
                .nodeConfig(config)
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
