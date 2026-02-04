package com.aiinpocket.n3n.credential.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int PBKDF2_ITERATIONS = 310000; // OWASP 2023 recommendation

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;
    private final byte[] instanceSalt;

    public EncryptionService(@Value("${app.encryption-key:${JWT_SECRET:default-key-change-in-production}}") String encryptionKey,
                            @Value("${N3N_INSTANCE_SALT:#{null}}") String instanceSaltEnv) {
        // Generate or load instance salt
        this.instanceSalt = loadOrGenerateInstanceSalt(instanceSaltEnv);

        // Derive a 256-bit key using PBKDF2 (secure key derivation)
        this.secretKey = deriveKeySecurely(encryptionKey);
        this.secureRandom = new SecureRandom();

        // Warn if using default key in production
        if (encryptionKey.equals("default-key-change-in-production")) {
            log.warn("⚠️ Using default encryption key. Set app.encryption-key or JWT_SECRET in production!");
        }
    }

    /**
     * Securely derive a 256-bit AES key using PBKDF2-HMAC-SHA256
     * Following OWASP Password Storage Cheat Sheet recommendations
     */
    private SecretKey deriveKeySecurely(String password) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                instanceSalt,
                PBKDF2_ITERATIONS,
                256 // key length in bits
            );

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] derivedKey = factory.generateSecret(spec).getEncoded();

            // Clear sensitive data
            spec.clearPassword();

            return new SecretKeySpec(derivedKey, "AES");
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Failed to derive encryption key using PBKDF2", e);
        }
    }

    /**
     * Load instance salt from environment or generate a new one
     * Instance salt should be stored securely and consistently across restarts
     */
    private byte[] loadOrGenerateInstanceSalt(String envSalt) {
        if (envSalt != null && !envSalt.isBlank()) {
            try {
                return Base64.getDecoder().decode(envSalt);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid N3N_INSTANCE_SALT format, generating new salt");
            }
        }

        // Generate new random salt (32 bytes)
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);

        log.info("Generated new instance salt. For production, set N3N_INSTANCE_SALT={}",
                Base64.getEncoder().encodeToString(salt));

        return salt;
    }

    public record EncryptedData(byte[] ciphertext, byte[] iv) {}

    public EncryptedData encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedData(ciphertext, iv);
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }

    public String decrypt(byte[] ciphertext, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Failed to decrypt data", e);
        }
    }

    public String encryptToBase64(String plaintext) {
        EncryptedData encrypted = encrypt(plaintext);
        return Base64.getEncoder().encodeToString(encrypted.iv()) + ":" +
               Base64.getEncoder().encodeToString(encrypted.ciphertext());
    }

    public String decryptFromBase64(String encryptedBase64) {
        String[] parts = encryptedBase64.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid encrypted data format");
        }
        byte[] iv = Base64.getDecoder().decode(parts[0]);
        byte[] ciphertext = Base64.getDecoder().decode(parts[1]);
        return decrypt(ciphertext, iv);
    }
}
