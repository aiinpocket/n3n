package com.aiinpocket.n3n.backup.service;

import com.aiinpocket.n3n.ai.entity.AiProviderConfig;
import com.aiinpocket.n3n.ai.repository.AiProviderConfigRepository;
import com.aiinpocket.n3n.backup.config.CloudSyncProperties;
import com.aiinpocket.n3n.backup.dto.response.CloudSyncImportResult;
import com.aiinpocket.n3n.backup.dto.response.CloudSyncManifest;
import com.aiinpocket.n3n.backup.dto.response.CloudSyncStatus;
import com.aiinpocket.n3n.backup.event.AiProviderSyncEvent;
import com.aiinpocket.n3n.backup.event.CredentialSyncEvent;
import com.aiinpocket.n3n.backup.event.FlowSyncEvent;
import com.aiinpocket.n3n.backup.event.SyncAction;
import com.aiinpocket.n3n.backup.storage.CloudStorageFactory;
import com.aiinpocket.n3n.backup.storage.CloudStorageProvider;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.credential.entity.Credential;
import com.aiinpocket.n3n.credential.repository.CredentialRepository;
import com.aiinpocket.n3n.credential.service.MasterKeyProvider;
import com.aiinpocket.n3n.credential.service.RecoveryKeyService;
import com.aiinpocket.n3n.flow.entity.Flow;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CloudSyncServiceTest extends BaseServiceTest {

    @Mock private CloudSyncProperties properties;
    @Mock private CloudStorageFactory storageFactory;
    @Mock private BackupEncryptionService encryptionService;
    @Mock private MasterKeyProvider masterKeyProvider;
    @Mock private RecoveryKeyService recoveryKeyService;
    @Mock private FlowRepository flowRepository;
    @Mock private CredentialRepository credentialRepository;
    @Mock private AiProviderConfigRepository aiProviderConfigRepository;
    @Mock private CloudStorageProvider storageProvider;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    private CloudSyncService cloudSyncService;
    private SecretKey testMasterKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256);
        testMasterKey = keyGen.generateKey();

        lenient().when(masterKeyProvider.getMasterKey()).thenReturn(testMasterKey);
        lenient().when(masterKeyProvider.getCurrentKeyVersion()).thenReturn(1);
        lenient().when(properties.isEnabled()).thenReturn(true);
        lenient().when(properties.getProvider()).thenReturn("gcs");
        lenient().when(properties.getBucket()).thenReturn("test-bucket");
        lenient().when(properties.getBasePath()).thenReturn("sync/");
        lenient().when(properties.getServiceAccountJson()).thenReturn("{\"test\":true}");
        lenient().when(storageFactory.create(any())).thenReturn(storageProvider);

        cloudSyncService = new CloudSyncService(
                properties, storageFactory, encryptionService,
                masterKeyProvider, recoveryKeyService, objectMapper,
                flowRepository, credentialRepository, aiProviderConfigRepository);
    }

    // ========== getSyncStatus ==========

    @Test
    void getSyncStatus_whenConfigured_returnsEnabled() {
        CloudSyncStatus status = cloudSyncService.getSyncStatus();
        assertThat(status.isEnabled()).isTrue();
        assertThat(status.getProvider()).isEqualTo("gcs");
        assertThat(status.getFingerprint()).isNotNull();
        assertThat(status.getFingerprint()).hasSize(64);
    }

    @Test
    void getSyncStatus_whenNotConfigured_returnsDisabled() {
        when(properties.getServiceAccountJson()).thenReturn("");

        CloudSyncStatus status = cloudSyncService.getSyncStatus();
        assertThat(status.isEnabled()).isFalse();
    }

    @Test
    void getSyncStatus_whenDisabled_returnsDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        CloudSyncStatus status = cloudSyncService.getSyncStatus();
        assertThat(status.isEnabled()).isFalse();
    }

    // ========== Default Provider ==========

    @Test
    void getSyncStatus_defaultProvider_returnsGatewayUrl() {
        when(properties.getProvider()).thenReturn("default");
        when(properties.getGatewayUrl()).thenReturn("https://example.com/fn");

        CloudSyncStatus status = cloudSyncService.getSyncStatus();
        assertThat(status.isEnabled()).isTrue();
        assertThat(status.getProvider()).isEqualTo("default");
        assertThat(status.getGatewayUrl()).isEqualTo("https://example.com/fn");
        assertThat(status.getFingerprint()).hasSize(64);
    }

    @Test
    void getSyncStatus_gcsProvider_noGatewayUrl() {
        CloudSyncStatus status = cloudSyncService.getSyncStatus();
        assertThat(status.getGatewayUrl()).isNull();
    }

    @Test
    void isConfigured_defaultProvider_withGateway_returnsTrue() {
        when(properties.getProvider()).thenReturn("default");
        when(properties.getGatewayUrl()).thenReturn("https://example.com/fn");
        lenient().when(storageFactory.createDefault(anyString(), anyString()))
                .thenReturn(storageProvider);

        // getSyncStatus internally calls isConfigured
        CloudSyncStatus status = cloudSyncService.getSyncStatus();
        assertThat(status.isEnabled()).isTrue();
    }

    @Test
    void isConfigured_defaultProvider_noGateway_returnsFalse() {
        when(properties.getProvider()).thenReturn("default");
        when(properties.getGatewayUrl()).thenReturn("");

        CloudSyncStatus status = cloudSyncService.getSyncStatus();
        assertThat(status.isEnabled()).isFalse();
    }

    @Test
    void onFlowSync_defaultProvider_usesCreateDefault() throws Exception {
        when(properties.getProvider()).thenReturn("default");
        when(properties.getGatewayUrl()).thenReturn("https://example.com/fn");
        when(storageFactory.createDefault(anyString(), anyString())).thenReturn(storageProvider);

        BackupEncryptionService.EncryptedPayload payload =
                new BackupEncryptionService.EncryptedPayload("iv", "data");
        when(encryptionService.encryptWithKey(any(), any())).thenReturn(payload);

        Flow flow = Flow.builder().id(UUID.randomUUID()).name("test").build();
        cloudSyncService.onFlowSync(new FlowSyncEvent(flow.getId(), SyncAction.UPSERT, flow));

        verify(storageFactory).createDefault(eq("https://example.com/fn"), anyString());
        verify(storageFactory, never()).create(any());
    }

    // ========== Event Listeners ==========

    @Test
    void onFlowSync_upsert_uploadsToCloud() throws Exception {
        BackupEncryptionService.EncryptedPayload payload =
                new BackupEncryptionService.EncryptedPayload("testIv", "testData");
        when(encryptionService.encryptWithKey(any(), any())).thenReturn(payload);

        Flow flow = Flow.builder().id(UUID.randomUUID()).name("test-flow").build();
        FlowSyncEvent event = new FlowSyncEvent(flow.getId(), SyncAction.UPSERT, flow);

        cloudSyncService.onFlowSync(event);

        verify(storageProvider).upload(contains(".json.enc"), any(byte[].class));
    }

    @Test
    void onFlowSync_delete_deletesFromCloud() throws Exception {
        UUID flowId = UUID.randomUUID();
        FlowSyncEvent event = new FlowSyncEvent(flowId, SyncAction.DELETE, null);

        cloudSyncService.onFlowSync(event);

        verify(storageProvider).delete(contains(flowId.toString()));
    }

    @Test
    void onCredentialSync_upsert_uploadsToCloud() throws Exception {
        BackupEncryptionService.EncryptedPayload payload =
                new BackupEncryptionService.EncryptedPayload("testIv", "testData");
        when(encryptionService.encryptWithKey(any(), any())).thenReturn(payload);

        Credential credential = Credential.builder()
                .id(UUID.randomUUID())
                .name("test-cred")
                .type("api_key")
                .ownerId(UUID.randomUUID())
                .encryptedData(new byte[]{1, 2, 3})
                .encryptionIv(new byte[]{4, 5, 6})
                .build();
        CredentialSyncEvent event = new CredentialSyncEvent(
                credential.getId(), SyncAction.UPSERT, credential);

        cloudSyncService.onCredentialSync(event);

        verify(storageProvider).upload(contains("credentials"), any(byte[].class));
    }

    @Test
    void onAiProviderSync_delete_deletesFromCloud() throws Exception {
        UUID configId = UUID.randomUUID();
        AiProviderSyncEvent event = new AiProviderSyncEvent(configId, SyncAction.DELETE, null);

        cloudSyncService.onAiProviderSync(event);

        verify(storageProvider).delete(contains(configId.toString()));
    }

    @Test
    void onFlowSync_whenNotConfigured_doesNothing() throws Exception {
        when(properties.getServiceAccountJson()).thenReturn("");

        Flow flow = Flow.builder().id(UUID.randomUUID()).name("test").build();
        cloudSyncService.onFlowSync(new FlowSyncEvent(flow.getId(), SyncAction.UPSERT, flow));

        verify(storageProvider, never()).upload(any(), any());
    }

    @Test
    void onFlowSync_whenUploadFails_doesNotThrow() throws Exception {
        BackupEncryptionService.EncryptedPayload payload =
                new BackupEncryptionService.EncryptedPayload("iv", "data");
        when(encryptionService.encryptWithKey(any(), any())).thenReturn(payload);
        doThrow(new RuntimeException("Upload failed")).when(storageProvider).upload(any(), any());

        Flow flow = Flow.builder().id(UUID.randomUUID()).name("test").build();

        // Should not throw - errors are caught internally
        cloudSyncService.onFlowSync(new FlowSyncEvent(flow.getId(), SyncAction.UPSERT, flow));
    }

    // ========== listRemoteEntities ==========

    @Test
    void listRemoteEntities_invalidKey_throwsException() {
        when(recoveryKeyService.validate("invalid")).thenReturn(false);

        assertThatThrownBy(() -> cloudSyncService.listRemoteEntities("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid Recovery Key format");
    }

    @Test
    void listRemoteEntities_returnsManifest() throws Exception {
        String phrase = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12";
        when(recoveryKeyService.validate(phrase)).thenReturn(true);
        when(recoveryKeyService.deriveMasterKey(phrase)).thenReturn(testMasterKey.getEncoded());

        String fingerprint = cloudSyncService.getSyncStatus().getFingerprint();

        List<CloudStorageProvider.StorageFileInfo> files = List.of(
                new CloudStorageProvider.StorageFileInfo(
                        fingerprint + "/flows/uuid1.json.enc", 100, "2026-02-10"),
                new CloudStorageProvider.StorageFileInfo(
                        fingerprint + "/credentials/uuid2.json.enc", 200, "2026-02-10"),
                new CloudStorageProvider.StorageFileInfo(
                        fingerprint + "/ai-providers/uuid3.json.enc", 50, "2026-02-10")
        );
        when(storageProvider.list(anyString())).thenReturn(files);

        CloudSyncManifest manifest = cloudSyncService.listRemoteEntities(phrase);

        assertThat(manifest.getFlowCount()).isEqualTo(1);
        assertThat(manifest.getCredentialCount()).isEqualTo(1);
        assertThat(manifest.getAiProviderCount()).isEqualTo(1);
        assertThat(manifest.getEntities()).hasSize(3);
    }

    @Test
    void listRemoteEntities_emptyStorage_returnsEmptyManifest() throws Exception {
        String phrase = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12";
        when(recoveryKeyService.validate(phrase)).thenReturn(true);
        when(recoveryKeyService.deriveMasterKey(phrase)).thenReturn(testMasterKey.getEncoded());
        when(storageProvider.list(anyString())).thenReturn(List.of());

        CloudSyncManifest manifest = cloudSyncService.listRemoteEntities(phrase);

        assertThat(manifest.getFlowCount()).isEqualTo(0);
        assertThat(manifest.getEntities()).isEmpty();
    }

    // ========== importFromRecoveryKey ==========

    @Test
    void importFromRecoveryKey_invalidKey_throwsException() {
        when(recoveryKeyService.validate("bad")).thenReturn(false);

        assertThatThrownBy(() -> cloudSyncService.importFromRecoveryKey("bad", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void importFromRecoveryKey_emptyStorage_returnsZeroCounts() throws Exception {
        String phrase = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12";
        when(recoveryKeyService.validate(phrase)).thenReturn(true);
        when(recoveryKeyService.deriveMasterKey(phrase)).thenReturn(testMasterKey.getEncoded());
        when(storageProvider.list(anyString())).thenReturn(List.of());

        CloudSyncImportResult result = cloudSyncService.importFromRecoveryKey(phrase, UUID.randomUUID());

        assertThat(result.getFlowsImported()).isEqualTo(0);
        assertThat(result.getCredentialsImported()).isEqualTo(0);
        assertThat(result.getAiProvidersImported()).isEqualTo(0);
        assertThat(result.getFailed()).isEqualTo(0);
    }

    @Test
    void importFromRecoveryKey_skipsExistingEntities() throws Exception {
        String phrase = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12";
        when(recoveryKeyService.validate(phrase)).thenReturn(true);
        when(recoveryKeyService.deriveMasterKey(phrase)).thenReturn(testMasterKey.getEncoded());

        String fingerprint = cloudSyncService.getSyncStatus().getFingerprint();
        UUID existingFlowId = UUID.randomUUID();

        // Mock: entity file exists in cloud
        String flowPath = fingerprint + "/flows/" + existingFlowId + ".json.enc";
        List<CloudStorageProvider.StorageFileInfo> files = List.of(
                new CloudStorageProvider.StorageFileInfo(flowPath, 100, "2026-02-10")
        );
        when(storageProvider.list(anyString())).thenReturn(files);

        // Build encrypted envelope
        Flow flow = Flow.builder().id(existingFlowId).name("existing-flow").build();
        byte[] flowJson = objectMapper.writeValueAsBytes(flow);
        BackupEncryptionService.EncryptedPayload enc =
                new BackupEncryptionService.EncryptedPayload("testIv", "testData");

        // The download returns an envelope
        String envelopeJson = objectMapper.writeValueAsString(
                java.util.Map.of("iv", "testIv", "data", "testData"));
        when(storageProvider.download(flowPath)).thenReturn(envelopeJson.getBytes());

        // Mock decryption to return the flow JSON
        when(encryptionService.decryptWithKey(eq("testIv"), eq("testData"), any()))
                .thenReturn(flowJson);

        // Flow already exists locally
        when(flowRepository.findById(existingFlowId)).thenReturn(Optional.of(flow));

        CloudSyncImportResult result = cloudSyncService.importFromRecoveryKey(phrase, UUID.randomUUID());

        assertThat(result.getSkipped()).isEqualTo(1);
        assertThat(result.getFlowsImported()).isEqualTo(0);
        verify(flowRepository, never()).save(any());
    }
}
