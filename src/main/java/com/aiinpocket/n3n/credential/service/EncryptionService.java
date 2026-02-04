package com.aiinpocket.n3n.credential.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encryption Service - 負責資料加解密
 *
 * 使用 AES-256-GCM 進行加密，金鑰由 MasterKeyProvider 提供。
 * MasterKeyProvider 會從 Recovery Key（助記詞）衍生 Master Key，
 * 第一次啟動時自動產生助記詞，使用者需要備份。
 */
@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final MasterKeyProvider masterKeyProvider;
    private final SecureRandom secureRandom;

    public EncryptionService(MasterKeyProvider masterKeyProvider) {
        this.masterKeyProvider = masterKeyProvider;
        this.secureRandom = new SecureRandom();
        log.info("EncryptionService initialized with MasterKeyProvider (source: {})",
                masterKeyProvider.getKeySource());
    }

    /**
     * 取得當前的加密金鑰
     */
    private SecretKey getSecretKey() {
        return masterKeyProvider.getMasterKey();
    }

    public record EncryptedData(byte[] ciphertext, byte[] iv) {}

    public EncryptedData encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), parameterSpec);

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
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), parameterSpec);

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

    /**
     * 檢查是否需要設定 Recovery Key（首次部署）
     */
    public boolean needsRecoveryKeySetup() {
        return masterKeyProvider.needsRecoveryKeySetup();
    }

    /**
     * 取得待備份的 Recovery Key（首次設定時）
     */
    public com.aiinpocket.n3n.credential.dto.RecoveryKey getPendingRecoveryKey() {
        return masterKeyProvider.getPendingRecoveryKey();
    }
}
