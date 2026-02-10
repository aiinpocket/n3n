package com.aiinpocket.n3n.backup.service;

import com.aiinpocket.n3n.credential.service.RecoveryKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 備份加密服務
 * 使用 Recovery Key 衍生的金鑰進行 AES-256-GCM 加密
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupEncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final RecoveryKeyService recoveryKeyService;

    /**
     * 從 Recovery Key 計算 fingerprint（SHA-256 前 16 hex 字元）
     * 用於備份檔案命名，識別同一個 Recovery Key 的備份
     */
    public String calculateFingerprint(String recoveryKeyPhrase) {
        try {
            String normalized = recoveryKeyPhrase.trim().toLowerCase().replaceAll("\\s+", " ");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate fingerprint", e);
        }
    }

    /**
     * 加密備份資料
     * @param data 原始 JSON 資料
     * @param recoveryKeyPhrase Recovery Key 助記詞
     * @return { iv (Base64), encryptedData (Base64) }
     */
    public EncryptedPayload encrypt(byte[] data, String recoveryKeyPhrase) {
        try {
            byte[] keyBytes = recoveryKeyService.deriveMasterKey(recoveryKeyPhrase);
            SecretKeySpec key = new SecretKeySpec(keyBytes, ALGORITHM);

            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] encrypted = cipher.doFinal(data);

            return new EncryptedPayload(
                    Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(encrypted)
            );
        } catch (Exception e) {
            throw new RuntimeException("Backup encryption failed", e);
        }
    }

    /**
     * 解密備份資料
     */
    public byte[] decrypt(String ivBase64, String encryptedDataBase64, String recoveryKeyPhrase) {
        try {
            byte[] keyBytes = recoveryKeyService.deriveMasterKey(recoveryKeyPhrase);
            SecretKeySpec key = new SecretKeySpec(keyBytes, ALGORITHM);

            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] encrypted = Base64.getDecoder().decode(encryptedDataBase64);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Backup decryption failed. Incorrect Recovery Key or corrupted data.", e);
        }
    }

    /**
     * 計算 SHA-256 checksum
     */
    public String calculateChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Checksum calculation failed", e);
        }
    }

    /**
     * 使用指定 SecretKey 加密
     */
    public EncryptedPayload encryptWithKey(byte[] data, javax.crypto.SecretKey key) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);

            byte[] encrypted = cipher.doFinal(data);

            return new EncryptedPayload(
                    Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(encrypted)
            );
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * 使用指定 key bytes 解密
     */
    public byte[] decryptWithKey(String ivBase64, String encryptedDataBase64, byte[] keyBytes) {
        try {
            SecretKeySpec key = new SecretKeySpec(keyBytes, ALGORITHM);
            byte[] iv = Base64.getDecoder().decode(ivBase64);
            byte[] encrypted = Base64.getDecoder().decode(encryptedDataBase64);

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);

            return cipher.doFinal(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed. Incorrect Recovery Key or corrupted data.", e);
        }
    }

    public record EncryptedPayload(String iv, String data) {}
}
