package com.aiinpocket.n3n.backup.service;

import com.aiinpocket.n3n.ai.entity.AiProviderConfig;
import com.aiinpocket.n3n.ai.repository.AiProviderConfigRepository;
import com.aiinpocket.n3n.backup.config.CloudSyncProperties;
import com.aiinpocket.n3n.backup.dto.response.*;
import com.aiinpocket.n3n.backup.entity.BackupSettings;
import com.aiinpocket.n3n.backup.event.*;
import com.aiinpocket.n3n.backup.storage.CloudStorageFactory;
import com.aiinpocket.n3n.backup.storage.CloudStorageProvider;
import com.aiinpocket.n3n.credential.entity.Credential;
import com.aiinpocket.n3n.credential.repository.CredentialRepository;
import com.aiinpocket.n3n.credential.service.MasterKeyProvider;
import com.aiinpocket.n3n.credential.service.RecoveryKeyService;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/**
 * 雲端即時同步服務
 * 負責 CRUD 操作時的自動上傳/刪除，以及從遠端匯入資料
 */
@Service
@ConditionalOnProperty(name = "n3n.cloud-sync.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class CloudSyncService {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final CloudSyncProperties properties;
    private final CloudStorageFactory storageFactory;
    private final BackupEncryptionService encryptionService;
    private final MasterKeyProvider masterKeyProvider;
    private final RecoveryKeyService recoveryKeyService;
    private final ObjectMapper objectMapper;
    private final FlowRepository flowRepository;
    private final CredentialRepository credentialRepository;
    private final AiProviderConfigRepository aiProviderConfigRepository;

    public CloudSyncService(
            CloudSyncProperties properties,
            CloudStorageFactory storageFactory,
            BackupEncryptionService encryptionService,
            MasterKeyProvider masterKeyProvider,
            RecoveryKeyService recoveryKeyService,
            ObjectMapper objectMapper,
            FlowRepository flowRepository,
            CredentialRepository credentialRepository,
            AiProviderConfigRepository aiProviderConfigRepository) {
        this.properties = properties;
        this.storageFactory = storageFactory;
        this.encryptionService = encryptionService;
        this.masterKeyProvider = masterKeyProvider;
        this.recoveryKeyService = recoveryKeyService;
        this.objectMapper = objectMapper;
        this.flowRepository = flowRepository;
        this.credentialRepository = credentialRepository;
        this.aiProviderConfigRepository = aiProviderConfigRepository;
    }

    // ========== 事件監聽（非同步） ==========

    @Async("cloudSyncExecutor")
    @EventListener
    public void onFlowSync(FlowSyncEvent event) {
        handleSync(event.entityType(), event.entityId().toString(), event.action(), event.entity());
    }

    @Async("cloudSyncExecutor")
    @EventListener
    public void onCredentialSync(CredentialSyncEvent event) {
        handleSync(event.entityType(), event.entityId().toString(), event.action(), event.entity());
    }

    @Async("cloudSyncExecutor")
    @EventListener
    public void onAiProviderSync(AiProviderSyncEvent event) {
        handleSync(event.entityType(), event.entityId().toString(), event.action(), event.entity());
    }

    // ========== 核心同步邏輯 ==========

    private void handleSync(String entityType, String entityId, SyncAction action, Object entity) {
        if (!isConfigured()) return;

        try {
            String fingerprint = calculateFingerprint(masterKeyProvider.getMasterKey());
            String path = fingerprint + "/" + entityType + "/" + entityId + ".json.enc";

            if (action == SyncAction.UPSERT && entity != null) {
                byte[] entityJson = objectMapper.writeValueAsBytes(entity);
                byte[] encrypted = buildEncryptedEnvelope(entityJson, entityType, entityId);
                uploadToCloud(path, encrypted);
                log.debug("Cloud sync uploaded: {}", path);
            } else if (action == SyncAction.DELETE) {
                deleteFromCloud(path);
                log.debug("Cloud sync deleted: {}", path);
            }
        } catch (Exception e) {
            log.warn("Cloud sync failed for {}/{}: {}", entityType, entityId, e.getMessage());
        }
    }

    // ========== 匯入相關方法 ==========

    /**
     * 掃描遠端 fingerprint 下的所有 entity
     */
    public CloudSyncManifest listRemoteEntities(String recoveryKeyPhrase) {
        if (!recoveryKeyService.validate(recoveryKeyPhrase)) {
            throw new IllegalArgumentException("Invalid Recovery Key format");
        }

        byte[] remoteKeyBytes = recoveryKeyService.deriveMasterKey(recoveryKeyPhrase);
        SecretKeySpec remoteKey = new SecretKeySpec(remoteKeyBytes, "AES");
        String remoteFingerprint = calculateFingerprint(remoteKey);

        try (CloudStorageProvider provider = createProvider(remoteKey)) {
            List<CloudStorageProvider.StorageFileInfo> files = provider.list(remoteFingerprint + "/");

            List<SyncEntityInfo> entities = new ArrayList<>();
            int flowCount = 0, credentialCount = 0, aiProviderCount = 0;

            for (CloudStorageProvider.StorageFileInfo file : files) {
                String filename = file.filename();
                if (!filename.endsWith(".json.enc")) continue;

                // 解析路徑：{fingerprint}/{type}/{id}.json.enc
                String relativePath = filename;
                if (relativePath.startsWith(remoteFingerprint + "/")) {
                    relativePath = relativePath.substring(remoteFingerprint.length() + 1);
                }

                String[] parts = relativePath.split("/");
                if (parts.length < 2) continue;

                String type = parts[0];
                String id = parts[1].replace(".json.enc", "");

                switch (type) {
                    case "flows" -> flowCount++;
                    case "credentials" -> credentialCount++;
                    case "ai-providers" -> aiProviderCount++;
                    default -> { continue; }
                }

                entities.add(SyncEntityInfo.builder()
                        .type(type)
                        .id(id)
                        .updatedAt(file.lastModified())
                        .build());
            }

            return CloudSyncManifest.builder()
                    .fingerprint(remoteFingerprint)
                    .flowCount(flowCount)
                    .credentialCount(credentialCount)
                    .aiProviderCount(aiProviderCount)
                    .entities(entities)
                    .build();
        } catch (Exception e) {
            log.error("Failed to list remote sync entities: {}", e.getMessage());
            throw new RuntimeException("Failed to list remote sync entities: " + e.getMessage(), e);
        }
    }

    /**
     * 匯入遠端資料：下載 → 用舊 key 解密 → 用新 key 重加密 → 存 DB → 上傳到自己的 fingerprint
     */
    public CloudSyncImportResult importFromRecoveryKey(String recoveryKeyPhrase, UUID targetUserId) {
        if (!recoveryKeyService.validate(recoveryKeyPhrase)) {
            throw new IllegalArgumentException("Invalid Recovery Key format");
        }

        byte[] oldMasterKeyBytes = recoveryKeyService.deriveMasterKey(recoveryKeyPhrase);
        SecretKeySpec oldMasterKey = new SecretKeySpec(oldMasterKeyBytes, "AES");
        String remoteFingerprint = calculateFingerprint(oldMasterKey);

        SecretKey currentMasterKey = masterKeyProvider.getMasterKey();
        String localFingerprint = calculateFingerprint(currentMasterKey);

        int flowsImported = 0, credentialsImported = 0, aiProvidersImported = 0;
        int skipped = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        // 下載所有遠端檔案（用舊 key 的 fingerprint 認證，網路 I/O 在事務外）
        List<RemoteEntity> remoteEntities = new ArrayList<>();
        try (CloudStorageProvider provider = createProvider(oldMasterKey)) {
            List<CloudStorageProvider.StorageFileInfo> files = provider.list(remoteFingerprint + "/");

            for (CloudStorageProvider.StorageFileInfo file : files) {
                if (!file.filename().endsWith(".json.enc")) continue;
                try {
                    byte[] data = provider.download(file.filename());
                    remoteEntities.add(new RemoteEntity(file.filename(), data));
                } catch (Exception e) {
                    failed++;
                    log.warn("Download failed: {} - {}", file.filename(), e.getMessage());
                    errors.add("Download failed: " + file.filename());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to cloud storage: " + e.getMessage(), e);
        }

        // 解密、重新加密、存入 DB（短事務）
        for (RemoteEntity remote : remoteEntities) {
            try {
                String relativePath = remote.path();
                if (relativePath.startsWith(remoteFingerprint + "/")) {
                    relativePath = relativePath.substring(remoteFingerprint.length() + 1);
                }

                String[] parts = relativePath.split("/");
                if (parts.length < 2) continue;

                String type = parts[0];
                String entityId = parts[1].replace(".json.enc", "");

                // 解密信封
                byte[] entityJson = decryptEnvelope(remote.data(), oldMasterKey);

                switch (type) {
                    case "flows" -> {
                        UUID flowUuid = UUID.fromString(entityId);
                        if (flowRepository.findById(flowUuid).isPresent()) {
                            skipped++;
                            continue;
                        }
                        Flow flow = objectMapper.readValue(entityJson, Flow.class);
                        flow.setCreatedBy(targetUserId);
                        flowRepository.save(flow);
                        flowsImported++;

                        // 重新加密並上傳到本地 fingerprint
                        reEncryptAndUpload(localFingerprint, type, entityId, entityJson, currentMasterKey);
                    }
                    case "credentials" -> {
                        UUID credUuid = UUID.fromString(entityId);
                        if (credentialRepository.findById(credUuid).isPresent()) {
                            skipped++;
                            continue;
                        }
                        Credential credential = objectMapper.readValue(entityJson, Credential.class);
                        credential.setOwnerId(targetUserId);

                        // 重新加密 credential data
                        reEncryptCredentialData(credential, oldMasterKey, currentMasterKey);
                        credentialRepository.save(credential);
                        credentialsImported++;

                        // 重新序列化（含新加密資料）並上傳
                        byte[] newEntityJson = objectMapper.writeValueAsBytes(credential);
                        reEncryptAndUpload(localFingerprint, type, entityId, newEntityJson, currentMasterKey);
                    }
                    case "ai-providers" -> {
                        UUID configUuid = UUID.fromString(entityId);
                        if (aiProviderConfigRepository.findById(configUuid).isPresent()) {
                            skipped++;
                            continue;
                        }
                        AiProviderConfig config = objectMapper.readValue(entityJson, AiProviderConfig.class);
                        config.setOwnerId(targetUserId);
                        aiProviderConfigRepository.save(config);
                        aiProvidersImported++;

                        reEncryptAndUpload(localFingerprint, type, entityId, entityJson, currentMasterKey);
                    }
                    default -> skipped++;
                }
            } catch (Exception e) {
                failed++;
                errors.add("Import failed: " + remote.path() + " - " + e.getMessage());
                log.warn("Failed to import entity {}: {}", remote.path(), e.getMessage());
            }
        }

        log.info("Cloud sync import completed: flows={}, credentials={}, aiProviders={}, skipped={}, failed={}",
                flowsImported, credentialsImported, aiProvidersImported, skipped, failed);

        return CloudSyncImportResult.builder()
                .flowsImported(flowsImported)
                .credentialsImported(credentialsImported)
                .aiProvidersImported(aiProvidersImported)
                .skipped(skipped)
                .failed(failed)
                .errors(errors)
                .build();
    }

    /**
     * 取得同步狀態
     */
    public CloudSyncStatus getSyncStatus() {
        boolean configured = isConfigured();
        return CloudSyncStatus.builder()
                .enabled(configured)
                .provider(configured ? properties.getProvider() : null)
                .fingerprint(configured ? calculateFingerprint(masterKeyProvider.getMasterKey()) : null)
                .gatewayUrl("default".equalsIgnoreCase(properties.getProvider())
                        ? properties.getGatewayUrl() : null)
                .build();
    }

    // ========== 加密/解密工具 ==========

    private byte[] buildEncryptedEnvelope(byte[] entityJson, String entityType, String entityId) {
        try {
            SecretKey masterKey = masterKeyProvider.getMasterKey();
            BackupEncryptionService.EncryptedPayload encrypted =
                    encryptionService.encryptWithKey(entityJson, masterKey);

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("version", "1.0");
            envelope.put("platform", "n3n");
            envelope.put("fingerprint", calculateFingerprint(masterKey));
            envelope.put("entityType", entityType);
            envelope.put("entityId", entityId);
            envelope.put("createdAt", Instant.now().toString());
            envelope.put("encrypted", true);
            envelope.put("algorithm", "AES-256-GCM");
            envelope.put("iv", encrypted.iv());
            envelope.put("data", encrypted.data());

            return objectMapper.writeValueAsBytes(envelope);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build encrypted envelope", e);
        }
    }

    @SuppressWarnings("unchecked")
    private byte[] decryptEnvelope(byte[] envelopeBytes, SecretKey masterKey) {
        try {
            Map<String, Object> envelope = objectMapper.readValue(envelopeBytes, LinkedHashMap.class);
            String iv = (String) envelope.get("iv");
            String encryptedData = (String) envelope.get("data");
            return encryptionService.decryptWithKey(iv, encryptedData, masterKey.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to decrypt envelope", e);
        }
    }

    private void reEncryptCredentialData(Credential credential, SecretKey oldKey, SecretKey newKey) {
        try {
            // 用舊 key 解密
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, oldKey,
                    new GCMParameterSpec(GCM_TAG_LENGTH, credential.getEncryptionIv()));
            byte[] plaintext = cipher.doFinal(credential.getEncryptedData());

            // 用新 key 重新加密
            byte[] newIv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(newIv);
            cipher.init(Cipher.ENCRYPT_MODE, newKey,
                    new GCMParameterSpec(GCM_TAG_LENGTH, newIv));
            byte[] newEncrypted = cipher.doFinal(plaintext);

            credential.setEncryptedData(newEncrypted);
            credential.setEncryptionIv(newIv);
            credential.setKeyVersion(masterKeyProvider.getCurrentKeyVersion());
            credential.setKeyStatus("active");
        } catch (Exception e) {
            throw new RuntimeException("Failed to re-encrypt credential data", e);
        }
    }

    private void reEncryptAndUpload(String fingerprint, String type, String entityId,
                                     byte[] entityJson, SecretKey masterKey) {
        try {
            BackupEncryptionService.EncryptedPayload encrypted =
                    encryptionService.encryptWithKey(entityJson, masterKey);

            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("version", "1.0");
            envelope.put("platform", "n3n");
            envelope.put("fingerprint", fingerprint);
            envelope.put("entityType", type);
            envelope.put("entityId", entityId);
            envelope.put("createdAt", Instant.now().toString());
            envelope.put("encrypted", true);
            envelope.put("algorithm", "AES-256-GCM");
            envelope.put("iv", encrypted.iv());
            envelope.put("data", encrypted.data());

            byte[] envelopeBytes = objectMapper.writeValueAsBytes(envelope);
            String path = fingerprint + "/" + type + "/" + entityId + ".json.enc";
            uploadToCloud(path, envelopeBytes);
        } catch (Exception e) {
            log.warn("Failed to re-encrypt and upload {}/{}: {}", type, entityId, e.getMessage());
        }
    }

    // ========== 雲端操作 ==========

    private void uploadToCloud(String path, byte[] data) throws IOException {
        try (CloudStorageProvider provider = createProvider()) {
            provider.upload(path, data);
        }
    }

    private void deleteFromCloud(String path) throws IOException {
        try (CloudStorageProvider provider = createProvider()) {
            provider.delete(path);
        }
    }

    /**
     * 建立 CloudStorageProvider（使用當前 master key）
     */
    private CloudStorageProvider createProvider() {
        return createProvider(masterKeyProvider.getMasterKey());
    }

    /**
     * 建立 CloudStorageProvider（使用指定的 master key）
     * default provider: fingerprint 即 Bearer token，透過 Cloud Function 閘道
     * 其他 provider: 直接連接 GCS/S3/SFTP
     */
    private CloudStorageProvider createProvider(SecretKey masterKey) {
        if ("default".equalsIgnoreCase(properties.getProvider())) {
            return storageFactory.createDefault(
                    properties.getGatewayUrl(),
                    calculateFingerprint(masterKey));
        }
        return storageFactory.create(buildSettingsFromProperties());
    }

    // ========== 工具方法 ==========

    /**
     * 計算完整 SHA-256 fingerprint（64 hex chars = 256 bits）
     * fingerprint 同時作為 Cloud Function 的 Bearer token 和路徑前綴
     */
    private String calculateFingerprint(SecretKey masterKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(masterKey.getEncoded());
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate fingerprint", e);
        }
    }

    private BackupSettings buildSettingsFromProperties() {
        return BackupSettings.builder()
                .provider(properties.getProvider())
                .bucket(properties.getBucket())
                .basePath(properties.getBasePath())
                .endpoint(properties.getEndpoint())
                .region(properties.getRegion())
                .accessKey(properties.getAccessKey())
                .secretKey(properties.getSecretKey())
                .serviceAccountJson(properties.getServiceAccountJson())
                .build();
    }

    private boolean isConfigured() {
        if (!properties.isEnabled()) return false;
        String provider = properties.getProvider();
        if (provider == null) return false;
        return switch (provider.toLowerCase()) {
            case "default" -> properties.getGatewayUrl() != null
                    && !properties.getGatewayUrl().isBlank();
            case "gcs" -> properties.getServiceAccountJson() != null
                    && !properties.getServiceAccountJson().isBlank();
            case "s3", "r2" -> properties.getAccessKey() != null
                    && !properties.getAccessKey().isBlank();
            default -> false;
        };
    }

    private record RemoteEntity(String path, byte[] data) {}
}
