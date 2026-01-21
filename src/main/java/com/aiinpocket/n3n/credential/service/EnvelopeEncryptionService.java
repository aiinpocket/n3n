package com.aiinpocket.n3n.credential.service;

import com.aiinpocket.n3n.credential.entity.DataEncryptionKey;
import com.aiinpocket.n3n.credential.repository.DataEncryptionKeyRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * Envelope Encryption Service
 *
 * 使用兩層加密架構：
 * 1. Master Key (MK) - 加密 DEK，由 MasterKeyProvider 管理
 * 2. Data Encryption Key (DEK) - 加密實際資料
 *
 * 加密後的資料格式：
 * [版本號 4 bytes][IV 12 bytes][密文 N bytes]
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnvelopeEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;
    private static final int KEY_SIZE = 256;
    private static final String KEY_ALGORITHM = "AES";

    // DEK 預設有效期：90 天
    private static final int DEK_VALIDITY_DAYS = 90;

    private final MasterKeyProvider masterKeyProvider;
    private final DataEncryptionKeyRepository dekRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() {
        // 確保有可用的 DEK
        ensureActiveDekExists("CREDENTIAL");
    }

    /**
     * 加密敏感資料
     *
     * @param plaintext 原始資料
     * @param purpose   用途（CREDENTIAL, LOG, GENERAL）
     * @return Base64 編碼的加密資料（含版本號）
     */
    @Transactional
    public String encrypt(String plaintext, String purpose) {
        DataEncryptionKey dek = getOrCreateActiveDek(purpose);
        SecretKey dekKey = decryptDek(dek);

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, dekKey, parameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 組合：版本號 + IV + 密文
            ByteBuffer buffer = ByteBuffer.allocate(4 + GCM_IV_LENGTH + ciphertext.length);
            buffer.putInt(dek.getKeyVersion());
            buffer.put(iv);
            buffer.put(ciphertext);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Failed to encrypt data", e);
        }
    }

    /**
     * 解密敏感資料
     *
     * @param encryptedData Base64 編碼的加密資料
     * @param purpose       用途
     * @return 解密後的原始資料
     */
    public String decrypt(String encryptedData, String purpose) {
        try {
            byte[] data = Base64.getDecoder().decode(encryptedData);
            ByteBuffer buffer = ByteBuffer.wrap(data);

            int keyVersion = buffer.getInt();
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            // 取得對應版本的 DEK
            DataEncryptionKey dek = dekRepository.findByPurposeAndKeyVersion(purpose, keyVersion)
                .orElseThrow(() -> new RuntimeException("DEK version " + keyVersion + " not found"));

            if ("RETIRED".equals(dek.getStatus())) {
                throw new RuntimeException("DEK version " + keyVersion + " has been retired");
            }

            SecretKey dekKey = decryptDek(dek);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, dekKey, parameterSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Failed to decrypt data", e);
        }
    }

    /**
     * 加密敏感資料（使用預設用途）
     */
    public String encrypt(String plaintext) {
        return encrypt(plaintext, "CREDENTIAL");
    }

    /**
     * 解密敏感資料（使用預設用途）
     */
    public String decrypt(String encryptedData) {
        return decrypt(encryptedData, "CREDENTIAL");
    }

    /**
     * 輪換 DEK
     */
    @Transactional
    public DataEncryptionKey rotateDek(String purpose) {
        // 將目前的 ACTIVE DEK 改為 DECRYPT_ONLY
        dekRepository.findActiveGlobalKey(purpose).ifPresent(oldDek -> {
            oldDek.setStatus("DECRYPT_ONLY");
            oldDek.setRotatedAt(Instant.now());
            dekRepository.save(oldDek);
            log.info("DEK version {} demoted to DECRYPT_ONLY", oldDek.getKeyVersion());
        });

        // 建立新的 DEK
        DataEncryptionKey newDek = createNewDek(purpose, null);
        log.info("New DEK version {} created for purpose {}", newDek.getKeyVersion(), purpose);

        return newDek;
    }

    /**
     * 確保有啟用的 DEK
     */
    @Transactional
    public void ensureActiveDekExists(String purpose) {
        if (dekRepository.findActiveGlobalKey(purpose).isEmpty()) {
            createNewDek(purpose, null);
            log.info("Created initial DEK for purpose: {}", purpose);
        }
    }

    private DataEncryptionKey getOrCreateActiveDek(String purpose) {
        return dekRepository.findActiveGlobalKey(purpose)
            .orElseGet(() -> createNewDek(purpose, null));
    }

    @Transactional
    protected DataEncryptionKey createNewDek(String purpose, UUID workspaceId) {
        try {
            // 生成新的 DEK
            KeyGenerator keyGen = KeyGenerator.getInstance(KEY_ALGORITHM);
            keyGen.init(KEY_SIZE, secureRandom);
            SecretKey dek = keyGen.generateKey();

            // 用 Master Key 加密 DEK
            SecretKey masterKey = masterKeyProvider.getMasterKey();
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, parameterSpec);

            byte[] encryptedDek = cipher.doFinal(dek.getEncoded());

            // 取得新版本號
            int newVersion = dekRepository.findMaxVersion(purpose) + 1;

            DataEncryptionKey dekEntity = DataEncryptionKey.builder()
                .keyVersion(newVersion)
                .purpose(purpose)
                .encryptedKey(encryptedDek)
                .encryptionIv(iv)
                .status("ACTIVE")
                .workspaceId(workspaceId)
                .expiresAt(Instant.now().plus(DEK_VALIDITY_DAYS, ChronoUnit.DAYS))
                .build();

            return dekRepository.save(dekEntity);
        } catch (Exception e) {
            log.error("Failed to create new DEK", e);
            throw new RuntimeException("Failed to create data encryption key", e);
        }
    }

    private SecretKey decryptDek(DataEncryptionKey dek) {
        try {
            SecretKey masterKey = masterKeyProvider.getMasterKey();

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, dek.getEncryptionIv());
            cipher.init(Cipher.DECRYPT_MODE, masterKey, parameterSpec);

            byte[] dekBytes = cipher.doFinal(dek.getEncryptedKey());
            return new SecretKeySpec(dekBytes, KEY_ALGORITHM);
        } catch (Exception e) {
            log.error("Failed to decrypt DEK", e);
            throw new RuntimeException("Failed to decrypt data encryption key", e);
        }
    }

    /**
     * 重新加密資料（使用最新的 DEK）
     */
    @Transactional
    public String reEncrypt(String encryptedData, String purpose) {
        String plaintext = decrypt(encryptedData, purpose);
        return encrypt(plaintext, purpose);
    }

    /**
     * 取得目前 DEK 版本
     */
    public int getCurrentDekVersion(String purpose) {
        return dekRepository.findActiveGlobalKey(purpose)
            .map(DataEncryptionKey::getKeyVersion)
            .orElse(0);
    }
}
