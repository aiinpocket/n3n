package com.aiinpocket.n3n.backup.service;

import com.aiinpocket.n3n.ai.repository.AiProviderConfigRepository;
import com.aiinpocket.n3n.backup.dto.request.UpdateBackupSettingsRequest;
import com.aiinpocket.n3n.backup.entity.BackupHistory;
import com.aiinpocket.n3n.backup.entity.BackupSettings;
import com.aiinpocket.n3n.backup.repository.BackupHistoryRepository;
import com.aiinpocket.n3n.backup.repository.BackupSettingsRepository;
import com.aiinpocket.n3n.backup.storage.CloudStorageFactory;
import com.aiinpocket.n3n.backup.storage.CloudStorageProvider;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.credential.repository.CredentialRepository;
import com.aiinpocket.n3n.credential.service.MasterKeyProvider;
import com.aiinpocket.n3n.credential.service.RecoveryKeyService;
import com.aiinpocket.n3n.flow.repository.FlowRepository;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BackupServiceTest extends BaseServiceTest {

    @Mock private BackupSettingsRepository settingsRepository;
    @Mock private BackupHistoryRepository historyRepository;
    @Mock private CloudStorageFactory storageFactory;
    @Mock private BackupEncryptionService encryptionService;
    @Mock private RecoveryKeyService recoveryKeyService;
    @Mock private MasterKeyProvider masterKeyProvider;
    @Mock private CredentialRepository credentialRepository;
    @Mock private FlowRepository flowRepository;
    @Mock private FlowVersionRepository flowVersionRepository;
    @Mock private AiProviderConfigRepository aiProviderConfigRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private BackupService backupService;

    private BackupSettings defaultSettings;
    private SecretKey testMasterKey;

    @BeforeEach
    void setUp() throws Exception {
        defaultSettings = BackupSettings.builder()
                .enabled(true)
                .provider("s3")
                .endpoint("https://s3.amazonaws.com")
                .bucket("test-bucket")
                .basePath("backups/")
                .build();

        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, new SecureRandom());
        testMasterKey = keyGen.generateKey();
    }

    @Test
    void getSettings_shouldReturnSettings() {
        when(settingsRepository.getOrCreate()).thenReturn(defaultSettings);

        var result = backupService.getSettings();

        assertThat(result.getEnabled()).isTrue();
        assertThat(result.getProvider()).isEqualTo("s3");
        assertThat(result.getBucket()).isEqualTo("test-bucket");
    }

    @Test
    void updateSettings_shouldUpdateProvider() {
        when(settingsRepository.getOrCreate()).thenReturn(defaultSettings);
        when(settingsRepository.save(any())).thenReturn(defaultSettings);

        var request = new UpdateBackupSettingsRequest();
        request.setProvider("gcs");

        var result = backupService.updateSettings(request);

        verify(settingsRepository).save(any());
        assertThat(result).isNotNull();
    }

    @Test
    void testConnection_shouldDelegateToProvider() {
        when(settingsRepository.getOrCreate()).thenReturn(defaultSettings);
        CloudStorageProvider mockProvider = mock(CloudStorageProvider.class);
        when(storageFactory.create(any())).thenReturn(mockProvider);
        when(mockProvider.testConnection()).thenReturn(true);

        boolean result = backupService.testConnection();

        assertThat(result).isTrue();
        verify(mockProvider).testConnection();
    }

    @Test
    void createBackup_whenDisabled_shouldThrow() {
        defaultSettings.setEnabled(false);
        when(settingsRepository.getOrCreate()).thenReturn(defaultSettings);

        assertThatThrownBy(() -> backupService.createBackup(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not enabled");
    }

    @Test
    void createBackup_shouldEncryptAndUpload() throws Exception {
        when(settingsRepository.getOrCreate()).thenReturn(defaultSettings);
        when(masterKeyProvider.getMasterKey()).thenReturn(testMasterKey);
        when(credentialRepository.findAll()).thenReturn(List.of());
        when(flowRepository.findAll()).thenReturn(List.of());
        when(flowVersionRepository.findAll()).thenReturn(List.of());
        when(aiProviderConfigRepository.findAll()).thenReturn(List.of());
        when(encryptionService.calculateChecksum(any())).thenReturn("test-checksum");
        when(encryptionService.encryptWithKey(any(), any())).thenReturn(
                new BackupEncryptionService.EncryptedPayload("dGVzdC1pdg==", "ZW5jcnlwdGVk"));

        CloudStorageProvider mockProvider = mock(CloudStorageProvider.class);
        when(storageFactory.create(any())).thenReturn(mockProvider);
        when(historyRepository.save(any())).thenAnswer(i -> {
            BackupHistory h = i.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });

        var result = backupService.createBackup(UUID.randomUUID());

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("completed");
        verify(mockProvider).upload(any(), any());
        verify(settingsRepository).save(any()); // lastBackupAt updated
    }

    @Test
    void listRemoteBackups_invalidRecoveryKey_shouldThrow() {
        when(recoveryKeyService.validate(any())).thenReturn(false);

        assertThatThrownBy(() -> backupService.listRemoteBackups("invalid-key"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Recovery Key");
    }

    @Test
    void restoreBackup_invalidRecoveryKey_shouldThrow() {
        when(recoveryKeyService.validate(any())).thenReturn(false);

        assertThatThrownBy(() ->
                backupService.restoreBackup("invalid", "file.enc", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Recovery Key");
    }

    @Test
    void restoreBackup_pathTraversal_shouldThrow() {
        when(recoveryKeyService.validate(any())).thenReturn(true);

        assertThatThrownBy(() ->
                backupService.restoreBackup("valid key phrase", "../../../etc/passwd", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid filename");
    }

    @Test
    void restoreBackup_slashInFilename_shouldThrow() {
        when(recoveryKeyService.validate(any())).thenReturn(true);

        assertThatThrownBy(() ->
                backupService.restoreBackup("valid key phrase", "path/to/file.enc", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid filename");
    }

    @Test
    void getHistory_shouldReturnPaged() {
        when(historyRepository.findAllByOrderByCreatedAtDesc(any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        var result = backupService.getHistory(org.springframework.data.domain.Pageable.unpaged());

        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    void testConnection_noProviderConfigured_shouldThrow() {
        var emptySettings = BackupSettings.builder().build();
        when(settingsRepository.getOrCreate()).thenReturn(emptySettings);

        assertThatThrownBy(() -> backupService.testConnection())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void testConnection_blankProvider_shouldThrow() {
        var settings = BackupSettings.builder().provider("  ").build();
        when(settingsRepository.getOrCreate()).thenReturn(settings);

        assertThatThrownBy(() -> backupService.testConnection())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void createBackup_uploadFails_shouldRecordFailure() throws Exception {
        when(settingsRepository.getOrCreate()).thenReturn(defaultSettings);
        when(masterKeyProvider.getMasterKey()).thenReturn(testMasterKey);
        when(credentialRepository.findAll()).thenReturn(List.of());
        when(flowRepository.findAll()).thenReturn(List.of());
        when(flowVersionRepository.findAll()).thenReturn(List.of());
        when(aiProviderConfigRepository.findAll()).thenReturn(List.of());
        when(encryptionService.calculateChecksum(any())).thenReturn("test-checksum");
        when(encryptionService.encryptWithKey(any(), any())).thenReturn(
                new BackupEncryptionService.EncryptedPayload("dGVzdC1pdg==", "ZW5jcnlwdGVk"));

        CloudStorageProvider mockProvider = mock(CloudStorageProvider.class);
        when(storageFactory.create(any())).thenReturn(mockProvider);
        doThrow(new java.io.IOException("Upload failed")).when(mockProvider).upload(any(), any());
        when(historyRepository.save(any())).thenAnswer(i -> {
            BackupHistory h = i.getArgument(0);
            h.setId(UUID.randomUUID());
            return h;
        });

        var result = backupService.createBackup(UUID.randomUUID());

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("failed");
        assertThat(result.getErrorMessage()).contains("Upload failed");
    }

    @Test
    void restoreBackup_backslashInFilename_shouldThrow() {
        when(recoveryKeyService.validate(any())).thenReturn(true);

        assertThatThrownBy(() ->
                backupService.restoreBackup("valid key phrase", "path\\file.enc", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid filename");
    }

    @Test
    void getSettings_shouldMaskSensitiveFields() {
        defaultSettings.setAccessKey("enc:some-encrypted-value");
        defaultSettings.setSecretKey("enc:another-encrypted-value");
        when(settingsRepository.getOrCreate()).thenReturn(defaultSettings);

        var result = backupService.getSettings();

        assertThat(result.getHasAccessKey()).isTrue();
        assertThat(result.getHasSecretKey()).isTrue();
    }

    @Test
    void updateSettings_shouldOnlyUpdateProvidedFields() {
        when(settingsRepository.getOrCreate()).thenReturn(defaultSettings);
        when(settingsRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        var request = new UpdateBackupSettingsRequest();
        request.setBucket("new-bucket");
        // provider, endpoint, etc. are null → should not change

        backupService.updateSettings(request);

        verify(settingsRepository).save(argThat(settings ->
                settings.getProvider().equals("s3") &&
                settings.getBucket().equals("new-bucket")
        ));
    }
}
