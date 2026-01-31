package com.aiinpocket.n3n.credential.service;

import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.repository.UserRepository;
import com.aiinpocket.n3n.credential.dto.RecoveryKey;
import com.aiinpocket.n3n.credential.entity.Credential;
import com.aiinpocket.n3n.credential.entity.EncryptionKeyMetadata;
import com.aiinpocket.n3n.credential.entity.KeyMigrationLog;
import com.aiinpocket.n3n.credential.repository.CredentialRepository;
import com.aiinpocket.n3n.credential.repository.EncryptionKeyMetadataRepository;
import com.aiinpocket.n3n.credential.repository.KeyMigrationLogRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    @Value("${N3N_INSTANCE_SALT:#{null}}")
    private String envInstanceSalt;

    @Value("${app.auto-generate-key:false}")
    private boolean autoGenerateKey;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    private final RecoveryKeyService recoveryKeyService;
    private final EncryptionKeyMetadataRepository metadataRepository;
    private final KeyMigrationLogRepository migrationLogRepository;
    private final CredentialRepository credentialRepository;
    private final UserRepository userRepository;

    private SecretKey masterKey;
    private String keySource;
    private byte[] instanceSalt;
    private boolean needsRecoveryKeySetup = false;
    private boolean keyMismatch = false;
    private Integer currentKeyVersion = 1;
    private RecoveryKey pendingRecoveryKey;

    public MasterKeyProvider(
            RecoveryKeyService recoveryKeyService,
            EncryptionKeyMetadataRepository metadataRepository,
            KeyMigrationLogRepository migrationLogRepository,
            CredentialRepository credentialRepository,
            UserRepository userRepository) {
        this.recoveryKeyService = recoveryKeyService;
        this.metadataRepository = metadataRepository;
        this.migrationLogRepository = migrationLogRepository;
        this.credentialRepository = credentialRepository;
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        // 1. 載入 Instance Salt
        this.instanceSalt = loadInstanceSalt();

        // 2. 載入 Master Key
        this.masterKey = loadMasterKey();

        // 3. 載入當前 Key 版本
        metadataRepository.findActiveByKeyType(EncryptionKeyMetadata.TYPE_RECOVERY)
                .ifPresent(meta -> this.currentKeyVersion = meta.getKeyVersion());

        log.info("Master key initialized from: {}", keySource);
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

        // 4. 生產環境必須設定金鑰，不允許自動生成
        if (isProductionProfile()) {
            throw new IllegalStateException(
                "SECURITY ERROR: Master key not configured in production environment. " +
                "Set N3N_MASTER_KEY environment variable or app.master-key property. " +
                "Auto-generation is disabled in production for security reasons."
            );
        }

        // 5. 首次部署：產生 Recovery Key
        keySource = "recovery-key-derived";

        // 產生新的 Recovery Key
        this.pendingRecoveryKey = recoveryKeyService.generate();
        this.needsRecoveryKeySetup = true;

        // 從 Recovery Key 衍生 Master Key
        byte[] derivedKey = recoveryKeyService.deriveMasterKey(
                pendingRecoveryKey.toPhrase(), instanceSalt);

        log.warn("⚠️ New Recovery Key generated. Admin must backup the key on first login.");
        return new SecretKeySpec(derivedKey, ALGORITHM);
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

    /**
     * 使用 PBKDF2 從密碼派生金鑰
     *
     * OWASP 建議：至少 310,000 次迭代 (2023 年標準)
     * https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
     */
    private byte[] deriveKey(String password) {
        try {
            // 固定 salt（因為需要確定性派生，每次用相同密碼得到相同金鑰）
            // 在實際使用中，密碼應該夠長夠複雜
            byte[] salt = "N3N-MASTER-KEY-SALT-2024".getBytes(java.nio.charset.StandardCharsets.UTF_8);

            // PBKDF2 with HMAC-SHA256, 310000 iterations (OWASP 2023 recommendation)
            PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                310000,  // 迭代次數
                256      // 輸出長度 (bits)
            );

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("PBKDF2 key derivation failed", e);
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

    // ==================== Recovery Key 相關方法 ====================

    /**
     * 檢查是否需要設定 Recovery Key（首次部署）
     */
    public boolean needsRecoveryKeySetup() {
        return needsRecoveryKeySetup;
    }

    /**
     * 檢查 Key 是否不匹配（資料可能被移動）
     */
    public boolean isKeyMismatch() {
        return keyMismatch;
    }

    /**
     * 取得當前 Key 版本
     */
    public Integer getCurrentKeyVersion() {
        return currentKeyVersion;
    }

    /**
     * 取得待備份的 Recovery Key（首次設定時）
     */
    public RecoveryKey getPendingRecoveryKey() {
        return pendingRecoveryKey;
    }

    /**
     * 確認 Recovery Key 備份
     */
    @Transactional
    public void confirmRecoveryKeyBackup(UUID userId, String recoveryKeyPhrase) {
        // 驗證 Recovery Key
        if (!recoveryKeyService.validate(recoveryKeyPhrase)) {
            throw new IllegalArgumentException("Invalid Recovery Key format");
        }

        // 驗證與 pending key 匹配
        if (pendingRecoveryKey != null) {
            String expectedPhrase = pendingRecoveryKey.toPhrase();
            if (!recoveryKeyPhrase.equalsIgnoreCase(expectedPhrase)) {
                throw new IllegalArgumentException("Recovery Key does not match");
            }
        }

        // 更新用戶備份狀態
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setRecoveryKeyBackedUp(true);
            user.setRecoveryKeyBackedUpAt(Instant.now());
            userRepository.save(user);
        }

        // 記錄元資料
        String keyHash = recoveryKeyService.calculateKeyHash(
                RecoveryKey.fromPhrase(recoveryKeyPhrase).getWords()
        );

        EncryptionKeyMetadata metadata = EncryptionKeyMetadata.builder()
                .keyType(EncryptionKeyMetadata.TYPE_RECOVERY)
                .keyVersion(currentKeyVersion)
                .keyHash(keyHash)
                .source(EncryptionKeyMetadata.SOURCE_AUTO_GENERATED)
                .status(EncryptionKeyMetadata.STATUS_ACTIVE)
                .build();
        metadataRepository.save(metadata);

        // 清除 pending key
        needsRecoveryKeySetup = false;
        pendingRecoveryKey = null;

        log.info("Recovery Key backup confirmed for user: {}", userId);
    }

    /**
     * 使用舊的 Recovery Key 遷移憑證
     */
    @Transactional
    public void migrateWithRecoveryKey(String oldRecoveryKeyPhrase, UUID credentialId, UUID userId) {
        if (!recoveryKeyService.validate(oldRecoveryKeyPhrase)) {
            throw new IllegalArgumentException("Invalid Recovery Key format");
        }

        Credential credential = credentialRepository.findById(credentialId)
                .orElseThrow(() -> new IllegalArgumentException("Credential not found: " + credentialId));

        // 建立遷移記錄
        KeyMigrationLog migrationLog = KeyMigrationLog.builder()
                .fromVersion(credential.getKeyVersion())
                .toVersion(currentKeyVersion)
                .credentialId(credentialId)
                .migratedBy(userId)
                .status(KeyMigrationLog.STATUS_IN_PROGRESS)
                .build();
        migrationLogRepository.save(migrationLog);

        try {
            // 用舊 Recovery Key 衍生舊 Master Key
            byte[] oldMasterKey = recoveryKeyService.deriveMasterKey(oldRecoveryKeyPhrase, instanceSalt);

            // TODO: 解密憑證資料並用新 Master Key 重新加密
            // 這需要整合 EnvelopeEncryptionService

            // 更新憑證狀態
            credential.setKeyVersion(currentKeyVersion);
            credential.setKeyStatus("active");
            credentialRepository.save(credential);

            // 標記遷移完成
            migrationLog.markCompleted();
            migrationLogRepository.save(migrationLog);

            log.info("Credential migrated successfully: {}", credentialId);

        } catch (Exception e) {
            migrationLog.markFailed(e.getMessage());
            migrationLogRepository.save(migrationLog);
            throw new RuntimeException("Migration failed: " + e.getMessage(), e);
        }
    }

    /**
     * 緊急還原（需要 Recovery Key + 永久密碼）
     */
    @Transactional
    public RecoveryKey emergencyRestore(String recoveryKeyPhrase, String permanentPassword, UUID userId) {
        if (!recoveryKeyService.validate(recoveryKeyPhrase)) {
            throw new IllegalArgumentException("Invalid Recovery Key format");
        }

        // 驗證永久密碼（可以是用戶密碼或特定的恢復密碼）
        // TODO: 實作永久密碼驗證邏輯

        // 產生新的 Recovery Key
        RecoveryKey newRecoveryKey = recoveryKeyService.generate();

        // 產生新的 Instance Salt
        this.instanceSalt = recoveryKeyService.generateInstanceSalt();

        // 用舊 Recovery Key 衍生舊 Master Key（使用舊 salt）
        // TODO: 需要載入舊的 instance salt

        // 用新 Recovery Key 衍生新 Master Key
        byte[] newMasterKeyBytes = recoveryKeyService.deriveMasterKey(
                newRecoveryKey.toPhrase(), instanceSalt);
        this.masterKey = new SecretKeySpec(newMasterKeyBytes, ALGORITHM);

        // 更新 key version
        this.currentKeyVersion++;

        // 記錄新的 Recovery Key 元資料
        EncryptionKeyMetadata metadata = EncryptionKeyMetadata.builder()
                .keyType(EncryptionKeyMetadata.TYPE_RECOVERY)
                .keyVersion(currentKeyVersion)
                .keyHash(newRecoveryKey.getKeyHash())
                .source(EncryptionKeyMetadata.SOURCE_AUTO_GENERATED)
                .status(EncryptionKeyMetadata.STATUS_ACTIVE)
                .build();
        metadataRepository.save(metadata);

        // 將舊版本標記為 deprecated
        metadataRepository.findByKeyTypeAndKeyVersion(
                EncryptionKeyMetadata.TYPE_RECOVERY, currentKeyVersion - 1)
                .ifPresent(old -> {
                    old.setStatus(EncryptionKeyMetadata.STATUS_DEPRECATED);
                    old.setRotatedAt(Instant.now());
                    metadataRepository.save(old);
                });

        log.warn("Emergency restore completed for user: {}", userId);
        return newRecoveryKey;
    }

    /**
     * 初始化 Instance Salt
     */
    private byte[] loadInstanceSalt() {
        // 1. 從環境變數載入
        if (envInstanceSalt != null && !envInstanceSalt.isBlank()) {
            log.info("Instance salt loaded from environment variable");
            return Base64.getDecoder().decode(envInstanceSalt);
        }

        // 2. 從資料庫載入
        Optional<EncryptionKeyMetadata> saltMetadata = metadataRepository
                .findActiveByKeyType(EncryptionKeyMetadata.TYPE_INSTANCE_SALT);

        if (saltMetadata.isPresent()) {
            // Salt hash 存在資料庫中，實際 salt 應該在環境變數
            log.warn("Instance salt metadata found but salt not in environment, generating new one");
        }

        // 3. 生成新的 Salt（首次部署）
        byte[] salt = recoveryKeyService.generateInstanceSalt();

        // 記錄元資料
        EncryptionKeyMetadata metadata = EncryptionKeyMetadata.builder()
                .keyType(EncryptionKeyMetadata.TYPE_INSTANCE_SALT)
                .keyVersion(1)
                .source(EncryptionKeyMetadata.SOURCE_AUTO_GENERATED)
                .status(EncryptionKeyMetadata.STATUS_ACTIVE)
                .build();
        metadataRepository.save(metadata);

        log.info("New instance salt generated. Set N3N_INSTANCE_SALT={} in production",
                Base64.getEncoder().encodeToString(salt));

        return salt;
    }
}
