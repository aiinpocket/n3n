package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/**
 * Handler for crypto nodes.
 * Performs cryptographic operations like hashing, encryption, and HMAC.
 */
@Component
@Slf4j
public class CryptoNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "crypto";
    }

    @Override
    public String getDisplayName() {
        return "Crypto";
    }

    @Override
    public String getDescription() {
        return "Performs cryptographic operations like hashing, encryption, and HMAC.";
    }

    @Override
    public String getCategory() {
        return "Tools";
    }

    @Override
    public String getIcon() {
        return "lock";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String operation = getStringConfig(context, "operation", "hash");
        String input = getStringConfig(context, "input", "");

        // If input is empty, try to get from input data
        if (input.isEmpty() && context.getInputData() != null) {
            Object data = context.getInputData().get("data");
            if (data != null) {
                input = data.toString();
            }
        }

        Map<String, Object> output = new HashMap<>();

        try {
            switch (operation) {
                case "hash":
                    String algorithm = getStringConfig(context, "algorithm", "SHA-256");
                    String encoding = getStringConfig(context, "encoding", "hex");

                    MessageDigest digest = MessageDigest.getInstance(algorithm);
                    byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

                    String hashResult = encodeBytes(hashBytes, encoding);
                    output.put("hash", hashResult);
                    output.put("algorithm", algorithm);
                    break;

                case "hmac":
                    String hmacAlgorithm = getStringConfig(context, "algorithm", "HmacSHA256");
                    String hmacKey = getStringConfig(context, "key", "");
                    String hmacEncoding = getStringConfig(context, "encoding", "hex");

                    Mac mac = Mac.getInstance(hmacAlgorithm);
                    SecretKeySpec keySpec = new SecretKeySpec(
                        hmacKey.getBytes(StandardCharsets.UTF_8), hmacAlgorithm);
                    mac.init(keySpec);
                    byte[] hmacBytes = mac.doFinal(input.getBytes(StandardCharsets.UTF_8));

                    output.put("hmac", encodeBytes(hmacBytes, hmacEncoding));
                    output.put("algorithm", hmacAlgorithm);
                    break;

                case "encrypt":
                    String encKey = getStringConfig(context, "key", "");
                    if (encKey.isEmpty()) {
                        return NodeExecutionResult.builder()
                            .success(false)
                            .errorMessage("Encryption key is required")
                            .build();
                    }

                    String encrypted = encryptAES(input, encKey);
                    output.put("encrypted", encrypted);
                    output.put("algorithm", "AES-GCM");
                    break;

                case "decrypt":
                    String decKey = getStringConfig(context, "key", "");
                    String ciphertext = getStringConfig(context, "ciphertext", input);

                    if (decKey.isEmpty()) {
                        return NodeExecutionResult.builder()
                            .success(false)
                            .errorMessage("Decryption key is required")
                            .build();
                    }

                    String decrypted = decryptAES(ciphertext, decKey);
                    output.put("decrypted", decrypted);
                    break;

                case "base64Encode":
                    output.put("encoded", Base64.getEncoder().encodeToString(
                        input.getBytes(StandardCharsets.UTF_8)));
                    break;

                case "base64Decode":
                    output.put("decoded", new String(
                        Base64.getDecoder().decode(input), StandardCharsets.UTF_8));
                    break;

                case "random":
                    int length = getIntConfig(context, "length", 32);
                    String format = getStringConfig(context, "format", "hex");

                    SecureRandom random = new SecureRandom();
                    byte[] randomBytes = new byte[length];
                    random.nextBytes(randomBytes);

                    output.put("random", encodeBytes(randomBytes, format));
                    output.put("length", length);
                    break;

                case "uuid":
                    output.put("uuid", UUID.randomUUID().toString());
                    break;

                default:
                    return NodeExecutionResult.builder()
                        .success(false)
                        .errorMessage("Unknown crypto operation: " + operation)
                        .build();
            }

            return NodeExecutionResult.builder()
                .success(true)
                .output(output)
                .build();

        } catch (Exception e) {
            log.error("Crypto operation failed: {}", e.getMessage(), e);
            return NodeExecutionResult.builder()
                .success(false)
                .errorMessage("Crypto operation failed: " + e.getMessage())
                .build();
        }
    }

    private String encodeBytes(byte[] bytes, String encoding) {
        switch (encoding.toLowerCase()) {
            case "base64":
                return Base64.getEncoder().encodeToString(bytes);
            case "hex":
            default:
                StringBuilder sb = new StringBuilder();
                for (byte b : bytes) {
                    sb.append(String.format("%02x", b));
                }
                return sb.toString();
        }
    }

    private String encryptAES(String plaintext, String key) throws Exception {
        byte[] keyBytes = deriveKey(key);
        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

        byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        // Combine IV and ciphertext
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

        return Base64.getEncoder().encodeToString(combined);
    }

    private String decryptAES(String ciphertext, String key) throws Exception {
        byte[] combined = Base64.getDecoder().decode(ciphertext);

        byte[] iv = Arrays.copyOfRange(combined, 0, 12);
        byte[] encrypted = Arrays.copyOfRange(combined, 12, combined.length);

        byte[] keyBytes = deriveKey(key);
        SecretKey secretKey = new SecretKeySpec(keyBytes, "AES");

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec parameterSpec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

        byte[] decrypted = cipher.doFinal(encrypted);
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private byte[] deriveKey(String key) throws Exception {
        // Derive a 256-bit key from the provided key string
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        return sha256.digest(key.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "operation", Map.of(
                    "type", "string",
                    "title", "Operation",
                    "enum", List.of("hash", "hmac", "encrypt", "decrypt",
                        "base64Encode", "base64Decode", "random", "uuid"),
                    "default", "hash"
                ),
                "input", Map.of(
                    "type", "string",
                    "title", "Input",
                    "description", "Data to process"
                ),
                "algorithm", Map.of(
                    "type", "string",
                    "title", "Algorithm",
                    "enum", List.of("SHA-256", "SHA-384", "SHA-512", "SHA-1", "MD5",
                        "HmacSHA256", "HmacSHA384", "HmacSHA512"),
                    "default", "SHA-256"
                ),
                "key", Map.of(
                    "type", "string",
                    "title", "Key",
                    "description", "Secret key for HMAC/encryption"
                ),
                "encoding", Map.of(
                    "type", "string",
                    "title", "Output Encoding",
                    "enum", List.of("hex", "base64"),
                    "default", "hex"
                ),
                "length", Map.of(
                    "type", "integer",
                    "title", "Length",
                    "description", "Length for random generation",
                    "default", 32
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "data", "type", "string", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "object")
            )
        );
    }
}
