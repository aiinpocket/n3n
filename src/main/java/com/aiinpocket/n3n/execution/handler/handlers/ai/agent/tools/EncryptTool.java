package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 加密/解密工具
 * 使用 AES-256-GCM 加密
 */
@Component
@Slf4j
public class EncryptTool implements AgentNodeTool {

    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATION_COUNT = 65536;
    private static final int SALT_LENGTH = 16;

    @Override
    public String getId() {
        return "encrypt";
    }

    @Override
    public String getName() {
        return "Encrypt/Decrypt";
    }

    @Override
    public String getDescription() {
        return """
                AES-256-GCM 加密/解密工具。

                操作類型：
                - encrypt: 加密文字
                - decrypt: 解密文字

                參數：
                - data: 要加密或解密的文字
                - password: 密碼
                - operation: encrypt 或 decrypt

                加密結果格式：Base64(salt + iv + ciphertext)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "data", Map.of(
                                "type", "string",
                                "description", "要加密或解密的文字"
                        ),
                        "password", Map.of(
                                "type", "string",
                                "description", "密碼"
                        ),
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("encrypt", "decrypt"),
                                "description", "操作類型",
                                "default", "encrypt"
                        )
                ),
                "required", List.of("data", "password")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String data = (String) parameters.get("data");
                String password = (String) parameters.get("password");
                String operation = (String) parameters.getOrDefault("operation", "encrypt");

                if (data == null || data.isEmpty()) {
                    return ToolResult.failure("資料不能為空");
                }
                if (password == null || password.isEmpty()) {
                    return ToolResult.failure("密碼不能為空");
                }

                // Security: limit input size
                if (data.length() > 10_000_000) {
                    return ToolResult.failure("資料過大，最大限制 10MB");
                }

                return switch (operation) {
                    case "encrypt" -> encrypt(data, password);
                    case "decrypt" -> decrypt(data, password);
                    default -> ToolResult.failure("不支援的操作: " + operation);
                };

            } catch (Exception e) {
                log.error("Encryption operation failed", e);
                return ToolResult.failure("加密操作失敗: " + e.getMessage());
            }
        });
    }

    private ToolResult encrypt(String plaintext, String password) {
        try {
            SecureRandom random = new SecureRandom();

            // Generate salt
            byte[] salt = new byte[SALT_LENGTH];
            random.nextBytes(salt);

            // Generate IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            random.nextBytes(iv);

            // Derive key from password
            SecretKey key = deriveKey(password, salt);

            // Encrypt
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));

            // Combine salt + iv + ciphertext
            byte[] combined = new byte[salt.length + iv.length + ciphertext.length];
            System.arraycopy(salt, 0, combined, 0, salt.length);
            System.arraycopy(iv, 0, combined, salt.length, iv.length);
            System.arraycopy(ciphertext, 0, combined, salt.length + iv.length, ciphertext.length);

            String result = Base64.getEncoder().encodeToString(combined);

            return ToolResult.success(
                    "加密成功\n結果：" + result,
                    Map.of(
                            "encrypted", result,
                            "algorithm", "AES-256-GCM",
                            "originalLength", plaintext.length(),
                            "encryptedLength", result.length()
                    )
            );
        } catch (Exception e) {
            return ToolResult.failure("加密失敗: " + e.getMessage());
        }
    }

    private ToolResult decrypt(String encryptedBase64, String password) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);

            if (combined.length < SALT_LENGTH + GCM_IV_LENGTH + 1) {
                return ToolResult.failure("無效的加密資料格式");
            }

            // Extract salt, iv, ciphertext
            byte[] salt = new byte[SALT_LENGTH];
            byte[] iv = new byte[GCM_IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - SALT_LENGTH - GCM_IV_LENGTH];

            System.arraycopy(combined, 0, salt, 0, SALT_LENGTH);
            System.arraycopy(combined, SALT_LENGTH, iv, 0, GCM_IV_LENGTH);
            System.arraycopy(combined, SALT_LENGTH + GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

            // Derive key from password
            SecretKey key = deriveKey(password, salt);

            // Decrypt
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);
            byte[] plaintext = cipher.doFinal(ciphertext);

            String result = new String(plaintext, "UTF-8");

            return ToolResult.success(
                    "解密成功\n結果：" + (result.length() > 500 ? result.substring(0, 500) + "..." : result),
                    Map.of(
                            "decrypted", result,
                            "algorithm", "AES-256-GCM",
                            "length", result.length()
                    )
            );
        } catch (Exception e) {
            return ToolResult.failure("解密失敗: " + e.getMessage());
        }
    }

    private SecretKey deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    @Override
    public String getCategory() {
        return "security";
    }

    @Override
    public boolean requiresConfirmation() {
        return false; // Encryption/decryption is a read-only operation on data
    }
}
