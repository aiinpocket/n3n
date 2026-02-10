package com.aiinpocket.n3n.backup.service;

import com.aiinpocket.n3n.ai.entity.AiProviderConfig;
import com.aiinpocket.n3n.ai.repository.AiProviderConfigRepository;
import com.aiinpocket.n3n.backup.dto.request.UpdateBackupSettingsRequest;
import com.aiinpocket.n3n.backup.dto.response.BackupHistoryResponse;
import com.aiinpocket.n3n.backup.dto.response.BackupSettingsResponse;
import com.aiinpocket.n3n.backup.dto.response.RemoteBackupInfo;
import com.aiinpocket.n3n.backup.entity.BackupHistory;
import com.aiinpocket.n3n.backup.entity.BackupSettings;
import com.aiinpocket.n3n.backup.repository.BackupHistoryRepository;
import com.aiinpocket.n3n.backup.repository.BackupSettingsRepository;
import com.aiinpocket.n3n.backup.storage.CloudStorageFactory;
import com.aiinpocket.n3n.backup.storage.CloudStorageProvider;
import com.aiinpocket.n3n.credential.entity.Credential;
import com.aiinpocket.n3n.credential.repository.CredentialRepository;
import com.aiinpocket.n3n.credential.service.MasterKeyProvider;
import com.aiinpocket.n3n.credential.service.RecoveryKeyService;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 雲端備份核心服務
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private final BackupSettingsRepository settingsRepository;
    private final BackupHistoryRepository historyRepository;
    private final CloudStorageFactory storageFactory;
    private final BackupEncryptionService encryptionService;
    private final RecoveryKeyService recoveryKeyService;
    private final MasterKeyProvider masterKeyProvider;
    private final CredentialRepository credentialRepository;
    private final FlowRepository flowRepository;
    private final FlowVersionRepository flowVersionRepository;
    private final AiProviderConfigRepository aiProviderConfigRepository;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss").withZone(ZoneOffset.UTC);

    // ========== Settings ==========

    public BackupSettingsResponse getSettings() {
        BackupSettings settings = settingsRepository.getOrCreate();
        return BackupSettingsResponse.from(settings);
    }

    @Transactional
    public BackupSettingsResponse updateSettings(UpdateBackupSettingsRequest request) {
        BackupSettings settings = settingsRepository.getOrCreate();

        if (request.getEnabled() != null) settings.setEnabled(request.getEnabled());
        if (request.getProvider() != null) settings.setProvider(request.getProvider());
        if (request.getEndpoint() != null) settings.setEndpoint(request.getEndpoint());
        if (request.getBucket() != null) settings.setBucket(request.getBucket());
        if (request.getBasePath() != null) settings.setBasePath(request.getBasePath());
        if (request.getRegion() != null) settings.setRegion(request.getRegion());
        if (request.getSftpHost() != null) settings.setSftpHost(request.getSftpHost());
        if (request.getSftpPort() != null) settings.setSftpPort(request.getSftpPort());
        if (request.getSftpUsername() != null) settings.setSftpUsername(request.getSftpUsername());
        if (request.getSftpPath() != null) settings.setSftpPath(request.getSftpPath());
        if (request.getSchedule() != null) settings.setSchedule(request.getSchedule());

        // 敏感欄位：加密後儲存（空字串表示未修改，不覆蓋）
        if (request.getAccessKey() != null && !request.getAccessKey().isBlank()) settings.setAccessKey(encryptField(request.getAccessKey()));
        if (request.getSecretKey() != null && !request.getSecretKey().isBlank()) settings.setSecretKey(encryptField(request.getSecretKey()));
        if (request.getServiceAccountJson() != null && !request.getServiceAccountJson().isBlank()) settings.setServiceAccountJson(encryptField(request.getServiceAccountJson()));
        if (request.getSftpPassword() != null && !request.getSftpPassword().isBlank()) settings.setSftpPassword(encryptField(request.getSftpPassword()));
        if (request.getSftpPrivateKey() != null && !request.getSftpPrivateKey().isBlank()) settings.setSftpPrivateKey(encryptField(request.getSftpPrivateKey()));

        settings = settingsRepository.save(settings);
        return BackupSettingsResponse.from(settings);
    }

    // ========== Test Connection ==========

    public boolean testConnection() {
        BackupSettings settings = settingsRepository.getOrCreate();
        if (settings.getProvider() == null || settings.getProvider().isBlank()) {
            throw new IllegalStateException("Backup provider not configured");
        }
        try (CloudStorageProvider provider = storageFactory.create(decryptSettings(settings))) {
            return provider.testConnection();
        }
    }

    // ========== Create Backup ==========

    /**
     * 建立備份：收集 → 加密 → 上傳 → 記錄
     * 網路 I/O (上傳) 在事務之外執行，避免長時間持有 DB 連線
     */
    public BackupHistoryResponse createBackup(UUID userId) {
        BackupSettings settings = settingsRepository.getOrCreate();
        if (!settings.getEnabled()) {
            throw new IllegalStateException("Backup is not enabled");
        }

        String provider = settings.getProvider();
        String filename = null;
        long fileSize = 0;
        String checksum = null;
        String errorMessage = null;

        try {
            // 1. 收集備份資料（讀取 DB，使用 repository 自身的 @Transactional）
            Map<String, Object> payload = collectBackupPayload();

            // 2. 序列化
            byte[] payloadBytes = objectMapper.writeValueAsBytes(payload);

            // 3. 計算 checksum
            checksum = encryptionService.calculateChecksum(payloadBytes);
            payload.put("checksum", checksum);
            payloadBytes = objectMapper.writeValueAsBytes(payload);

            // 4. 計算 fingerprint
            SecretKey masterKey = masterKeyProvider.getMasterKey();
            String fingerprint = calculateMasterKeyFingerprint(masterKey);

            // 5. 加密
            BackupEncryptionService.EncryptedPayload encrypted = encryptWithMasterKey(payloadBytes, masterKey);

            // 6. 組裝封包
            Map<String, Object> backupPackage = new LinkedHashMap<>();
            backupPackage.put("version", "1.0");
            backupPackage.put("platform", "n3n");
            backupPackage.put("fingerprint", fingerprint);
            backupPackage.put("createdAt", Instant.now().toString());
            backupPackage.put("encrypted", true);
            backupPackage.put("algorithm", "AES-256-GCM");
            backupPackage.put("iv", encrypted.iv());
            backupPackage.put("data", encrypted.data());

            byte[] packageBytes = objectMapper.writeValueAsBytes(backupPackage);

            // 7. 生成檔名並上傳（網路 I/O，不在事務中）
            String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
            filename = "n3n-" + fingerprint + "-" + timestamp + ".enc";

            try (CloudStorageProvider storageProvider = storageFactory.create(decryptSettings(settings))) {
                storageProvider.upload(filename, packageBytes);
            }

            fileSize = packageBytes.length;
            log.info("Backup created successfully: {} ({} bytes)", filename, fileSize);

        } catch (Exception e) {
            log.error("Backup creation failed: {}", e.getMessage(), e);
            errorMessage = e.getMessage();
        }

        // 8. 記錄歷史 + 更新 lastBackupAt（短事務）
        return saveBackupResult(provider, filename, fileSize, checksum, errorMessage, userId);
    }

    @Transactional
    protected BackupHistoryResponse saveBackupResult(
            String provider, String filename, long fileSize,
            String checksum, String errorMessage, UUID userId) {
        BackupHistory history = BackupHistory.builder()
                .provider(provider)
                .triggeredBy(userId)
                .build();

        if (errorMessage != null) {
            history.setFilename(filename != null ? filename : "backup-failed-" + Instant.now().toEpochMilli());
            history.markFailed(errorMessage);
        } else {
            history.setFilename(filename);
            history.setFileSize(fileSize);
            history.setChecksum(checksum);
            history.setStatus("completed");

            BackupSettings settings = settingsRepository.getOrCreate();
            settings.setLastBackupAt(Instant.now());
            settingsRepository.save(settings);
        }

        history = historyRepository.save(history);
        return BackupHistoryResponse.from(history);
    }

    // ========== List Remote Backups ==========

    public List<RemoteBackupInfo> listRemoteBackups(String recoveryKeyPhrase) {
        if (!recoveryKeyService.validate(recoveryKeyPhrase)) {
            throw new IllegalArgumentException("Invalid Recovery Key format");
        }

        BackupSettings settings = settingsRepository.getOrCreate();

        // 從 recovery key 衍生 master key，然後算 fingerprint
        byte[] masterKeyBytes = recoveryKeyService.deriveMasterKey(recoveryKeyPhrase);
        String fingerprint = calculateMasterKeyFingerprint(
                new javax.crypto.spec.SecretKeySpec(masterKeyBytes, "AES"));

        try (CloudStorageProvider provider = storageFactory.create(decryptSettings(settings))) {
            String prefix = "n3n-" + fingerprint;
            List<CloudStorageProvider.StorageFileInfo> files = provider.list(prefix);

            return files.stream()
                    .filter(f -> f.filename().endsWith(".enc"))
                    .map(f -> RemoteBackupInfo.builder()
                            .filename(f.filename())
                            .size(f.size())
                            .lastModified(f.lastModified())
                            .build())
                    .toList();
        } catch (Exception e) {
            log.error("Failed to list remote backups: {}", e.getMessage());
            throw new RuntimeException("Failed to list remote backups: " + e.getMessage(), e);
        }
    }

    // ========== Restore Backup ==========

    /**
     * 還原備份：下載 → 解密 → 驗證 → 匯入
     * 網路 I/O (下載) 在事務之外執行
     */
    public void restoreBackup(String recoveryKeyPhrase, String filename, UUID userId) {
        if (!recoveryKeyService.validate(recoveryKeyPhrase)) {
            throw new IllegalArgumentException("Invalid Recovery Key format");
        }

        // 服務層路徑遍歷防護（Defense-in-Depth）
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Invalid filename");
        }

        BackupSettings settings = settingsRepository.getOrCreate();

        try {
            // 1. 下載（網路 I/O，不在事務中）
            byte[] packageBytes;
            try (CloudStorageProvider provider = storageFactory.create(decryptSettings(settings))) {
                packageBytes = provider.download(filename);
            }

            // 2. 解析封包（使用 LinkedHashMap 保持 key 順序）
            @SuppressWarnings("unchecked")
            Map<String, Object> backupPackage = objectMapper.readValue(packageBytes, LinkedHashMap.class);

            String iv = (String) backupPackage.get("iv");
            String encryptedData = (String) backupPackage.get("data");

            // 3. 從 recovery key 衍生 master key 來解密
            byte[] masterKeyBytes = recoveryKeyService.deriveMasterKey(recoveryKeyPhrase);
            byte[] payloadBytes = decryptWithKey(iv, encryptedData, masterKeyBytes);

            // 4. 解析 payload（使用 LinkedHashMap 保持序列化順序一致）
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(payloadBytes, LinkedHashMap.class);

            // 5. 驗證 checksum（移除 checksum 欄位後重新序列化比對）
            String storedChecksum = (String) payload.remove("checksum");
            byte[] dataForChecksum = objectMapper.writeValueAsBytes(payload);
            String calculatedChecksum = encryptionService.calculateChecksum(dataForChecksum);

            if (storedChecksum == null || !storedChecksum.equals(calculatedChecksum)) {
                throw new SecurityException("Backup checksum mismatch - data may be corrupted");
            }

            // 6. 匯入資料（短事務）
            executeImport(payload);

            log.info("Backup restored successfully from: {}", filename);

        } catch (IllegalArgumentException | SecurityException e) {
            throw e;
        } catch (Exception e) {
            log.error("Backup restore failed: {}", e.getMessage(), e);
            throw new RuntimeException("Backup restore failed: " + e.getMessage(), e);
        }
    }

    @Transactional
    protected void executeImport(Map<String, Object> payload) {
        importPayload(payload);
    }

    // ========== History ==========

    public Page<BackupHistoryResponse> getHistory(Pageable pageable) {
        return historyRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(BackupHistoryResponse::from);
    }

    // ========== Private Methods ==========

    private Map<String, Object> collectBackupPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();

        // Master Key
        SecretKey masterKey = masterKeyProvider.getMasterKey();
        payload.put("masterKey", Base64.getEncoder().encodeToString(masterKey.getEncoded()));

        // Credentials (含加密資料，不解密)
        List<Credential> credentials = credentialRepository.findAll();
        payload.put("credentials", credentials);

        // Flows
        List<Flow> flows = flowRepository.findAll();
        payload.put("flows", flows);

        // Flow Versions
        List<FlowVersion> versions = flowVersionRepository.findAll();
        payload.put("flowVersions", versions);

        // AI Providers
        List<AiProviderConfig> aiProviders = aiProviderConfigRepository.findAll();
        payload.put("aiProviders", aiProviders);

        log.info("Collected backup: {} credentials, {} flows, {} versions, {} AI providers",
                credentials.size(), flows.size(), versions.size(), aiProviders.size());

        return payload;
    }

    private void importPayload(Map<String, Object> payload) {
        // 1. Master Key 還原 → 寫入 key file（必須先還原，才能正確使用 credential 加密）
        String masterKeyBase64 = (String) payload.get("masterKey");
        if (masterKeyBase64 != null) {
            byte[] masterKeyBytes = Base64.getDecoder().decode(masterKeyBase64);
            javax.crypto.spec.SecretKeySpec masterKey =
                    new javax.crypto.spec.SecretKeySpec(masterKeyBytes, "AES");
            masterKeyProvider.persistRestoredMasterKey(masterKey);
        }

        // 2. 匯入 Credentials
        Object credentialsRaw = payload.get("credentials");
        if (credentialsRaw instanceof List<?> list && !list.isEmpty()) {
            List<Credential> credentials = list.stream()
                    .map(item -> objectMapper.convertValue(item, Credential.class))
                    .toList();
            credentialRepository.saveAll(credentials);
            log.info("Restored {} credentials", credentials.size());
        }

        // 3. 匯入 Flows
        Object flowsRaw = payload.get("flows");
        if (flowsRaw instanceof List<?> list && !list.isEmpty()) {
            List<Flow> flows = list.stream()
                    .map(item -> objectMapper.convertValue(item, Flow.class))
                    .toList();
            flowRepository.saveAll(flows);
            log.info("Restored {} flows", flows.size());
        }

        // 4. 匯入 Flow Versions
        Object versionsRaw = payload.get("flowVersions");
        if (versionsRaw instanceof List<?> list && !list.isEmpty()) {
            List<FlowVersion> versions = list.stream()
                    .map(item -> objectMapper.convertValue(item, FlowVersion.class))
                    .toList();
            flowVersionRepository.saveAll(versions);
            log.info("Restored {} flow versions", versions.size());
        }

        // 5. 匯入 AI Providers
        Object aiProvidersRaw = payload.get("aiProviders");
        if (aiProvidersRaw instanceof List<?> list && !list.isEmpty()) {
            List<AiProviderConfig> aiProviders = list.stream()
                    .map(item -> objectMapper.convertValue(item, AiProviderConfig.class))
                    .toList();
            aiProviderConfigRepository.saveAll(aiProviders);
            log.info("Restored {} AI provider configs", aiProviders.size());
        }

        log.info("Backup data imported successfully.");
    }

    /**
     * 使用 Master Key 加密（委派給 BackupEncryptionService）
     */
    private BackupEncryptionService.EncryptedPayload encryptWithMasterKey(byte[] data, SecretKey masterKey) {
        return encryptionService.encryptWithKey(data, masterKey);
    }

    /**
     * 使用指定 key bytes 解密（委派給 BackupEncryptionService）
     */
    private byte[] decryptWithKey(String ivBase64, String encryptedBase64, byte[] keyBytes) {
        return encryptionService.decryptWithKey(ivBase64, encryptedBase64, keyBytes);
    }

    /**
     * 從 Master Key 計算 fingerprint
     */
    private String calculateMasterKeyFingerprint(SecretKey masterKey) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(masterKey.getEncoded());
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate fingerprint", e);
        }
    }

    // ========== Settings Field Encryption ==========

    private static final String FIELD_ENC_PREFIX = "enc:";

    /**
     * 加密敏感欄位（AES-256-GCM，使用 Master Key）
     * 格式：enc:<base64(IV + ciphertext)>
     */
    private String encryptField(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return plaintext;
        try {
            SecretKey key = masterKeyProvider.getMasterKey();
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // IV (12) + ciphertext
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return FIELD_ENC_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt settings field", e);
        }
    }

    /**
     * 解密敏感欄位
     */
    private String decryptField(String encrypted) {
        if (encrypted == null || !encrypted.startsWith(FIELD_ENC_PREFIX)) return encrypted;
        try {
            SecretKey key = masterKeyProvider.getMasterKey();
            byte[] combined = Base64.getDecoder().decode(encrypted.substring(FIELD_ENC_PREFIX.length()));
            byte[] iv = Arrays.copyOf(combined, 12);
            byte[] ciphertext = Arrays.copyOfRange(combined, 12, combined.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to decrypt settings field: {}", e.getMessage());
            return encrypted;
        }
    }

    /**
     * 建立解密後的 settings 副本，用於建立 CloudStorageProvider
     */
    private BackupSettings decryptSettings(BackupSettings settings) {
        BackupSettings decrypted = BackupSettings.builder()
                .id(settings.getId())
                .enabled(settings.getEnabled())
                .provider(settings.getProvider())
                .endpoint(settings.getEndpoint())
                .bucket(settings.getBucket())
                .basePath(settings.getBasePath())
                .region(settings.getRegion())
                .sftpHost(settings.getSftpHost())
                .sftpPort(settings.getSftpPort())
                .sftpUsername(settings.getSftpUsername())
                .sftpPath(settings.getSftpPath())
                .schedule(settings.getSchedule())
                .lastBackupAt(settings.getLastBackupAt())
                .build();
        // 解密敏感欄位
        decrypted.setAccessKey(decryptField(settings.getAccessKey()));
        decrypted.setSecretKey(decryptField(settings.getSecretKey()));
        decrypted.setServiceAccountJson(decryptField(settings.getServiceAccountJson()));
        decrypted.setSftpPassword(decryptField(settings.getSftpPassword()));
        decrypted.setSftpPrivateKey(decryptField(settings.getSftpPrivateKey()));
        return decrypted;
    }
}
