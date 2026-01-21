package com.aiinpocket.n3n.credential.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * Master Key Provider - 管理主加密金鑰
 *
 * 支援多種來源（優先順序）：
 * 1. 環境變數: N3N_MASTER_KEY
 * 2. 設定檔: app.master-key
 * 3. 金鑰檔案: app.master-key-file
 * 4. 自動生成（開發環境）
 *
 * 安全注意事項：
 * - 生產環境必須設定 N3N_MASTER_KEY 環境變數
 * - 金鑰長度必須為 256 bits (32 bytes)
 * - 金鑰遺失將導致所有加密資料無法還原
 */
@Component
@Slf4j
public class MasterKeyProvider {

    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;

    @Value("${N3N_MASTER_KEY:#{null}}")
    private String envMasterKey;

    @Value("${app.master-key:#{null}}")
    private String configMasterKey;

    @Value("${app.master-key-file:#{null}}")
    private String masterKeyFile;

    @Value("${app.auto-generate-key:false}")
    private boolean autoGenerateKey;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    private SecretKey masterKey;
    private String keySource;

    @PostConstruct
    public void init() {
        this.masterKey = loadMasterKey();
        log.info("Master key initialized from: {}", keySource);

        // 生產環境警告
        if (isProductionProfile() && "auto-generated".equals(keySource)) {
            log.error("⚠️ SECURITY WARNING: Using auto-generated master key in production!");
            log.error("⚠️ Set N3N_MASTER_KEY environment variable for production use.");
            log.error("⚠️ Auto-generated keys will change on restart, causing data loss!");
        }
    }

    private SecretKey loadMasterKey() {
        // 1. 環境變數（最高優先）
        if (envMasterKey != null && !envMasterKey.isBlank()) {
            keySource = "environment variable (N3N_MASTER_KEY)";
            return decodeKey(envMasterKey);
        }

        // 2. 設定檔
        if (configMasterKey != null && !configMasterKey.isBlank()) {
            keySource = "configuration (app.master-key)";
            return decodeKey(configMasterKey);
        }

        // 3. 金鑰檔案
        if (masterKeyFile != null && !masterKeyFile.isBlank()) {
            try {
                Path keyPath = Path.of(masterKeyFile);
                if (Files.exists(keyPath)) {
                    String keyContent = Files.readString(keyPath).trim();
                    keySource = "key file (" + masterKeyFile + ")";
                    return decodeKey(keyContent);
                }
            } catch (IOException e) {
                log.warn("Failed to read master key file: {}", e.getMessage());
            }
        }

        // 4. 自動生成（僅限開發環境）
        if (autoGenerateKey || !isProductionProfile()) {
            keySource = "auto-generated";
            return generateNewKey();
        }

        throw new IllegalStateException(
            "Master key not configured. Set N3N_MASTER_KEY environment variable or app.master-key property."
        );
    }

    private SecretKey decodeKey(String encodedKey) {
        try {
            byte[] keyBytes;

            // 支援 Base64 編碼或直接字串
            if (encodedKey.length() == 44 && encodedKey.endsWith("=")) {
                // Base64 encoded (32 bytes = 44 chars in Base64)
                keyBytes = Base64.getDecoder().decode(encodedKey);
            } else {
                // Direct string - hash to 32 bytes
                keyBytes = deriveKey(encodedKey);
            }

            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("Master key must be 256 bits (32 bytes)");
            }

            return new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid master key format", e);
        }
    }

    private byte[] deriveKey(String password) {
        // 使用 PBKDF2 或簡單 SHA-256 派生
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            return digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private SecretKey generateNewKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE, new SecureRandom());
            SecretKey key = keyGen.generateKey();

            // 輸出生成的金鑰供參考（僅開發環境）
            if (!isProductionProfile()) {
                String encodedKey = Base64.getEncoder().encodeToString(key.getEncoded());
                log.info("Generated new master key (save this for persistence): {}", encodedKey);
            }

            return key;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate master key", e);
        }
    }

    private boolean isProductionProfile() {
        return activeProfile != null &&
               (activeProfile.contains("prod") || activeProfile.contains("production"));
    }

    /**
     * 取得主金鑰
     */
    public SecretKey getMasterKey() {
        return masterKey;
    }

    /**
     * 取得金鑰來源（供診斷用）
     */
    public String getKeySource() {
        return keySource;
    }

    /**
     * 生成新的 Master Key（供管理員使用）
     * @return Base64 編碼的金鑰
     */
    public static String generateMasterKeyString() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE, new SecureRandom());
            SecretKey key = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate master key", e);
        }
    }

    /**
     * 將金鑰儲存到檔案（設定適當權限）
     */
    public static void saveKeyToFile(String key, String filePath) throws IOException {
        Path path = Path.of(filePath);
        Files.writeString(path, key);

        // 設定檔案權限為僅擁有者可讀（Unix-like 系統）
        try {
            Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ));
        } catch (UnsupportedOperationException e) {
            log.warn("Could not set file permissions (non-POSIX system)");
        }
    }
}
