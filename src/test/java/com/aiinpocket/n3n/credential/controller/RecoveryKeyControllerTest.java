package com.aiinpocket.n3n.credential.controller;

import com.aiinpocket.n3n.credential.dto.RecoveryKey;
import com.aiinpocket.n3n.credential.service.MasterKeyProvider;
import com.aiinpocket.n3n.credential.service.RecoveryKeyService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecoveryKeyControllerTest {

    @Mock
    private RecoveryKeyService recoveryKeyService;

    @Mock
    private MasterKeyProvider masterKeyProvider;

    @InjectMocks
    private RecoveryKeyController controller;

    // ===== Helpers =====

    private UserDetails testUser(UUID userId) {
        return User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    // ===== GET /status =====

    @Test
    void getSecurityStatus_shouldReturnStatus() {
        UUID userId = UUID.randomUUID();
        when(masterKeyProvider.needsRecoveryKeySetup()).thenReturn(false);
        when(masterKeyProvider.isKeyMismatch()).thenReturn(false);
        when(masterKeyProvider.getCurrentKeyVersion()).thenReturn(1);

        var response = controller.getSecurityStatus(testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isNeedsRecoveryKeySetup()).isFalse();
        assertThat(response.getBody().isKeyMismatch()).isFalse();
        assertThat(response.getBody().getCurrentKeyVersion()).isEqualTo(1);
    }

    @Test
    void getSecurityStatus_shouldIndicateSetupNeeded() {
        UUID userId = UUID.randomUUID();
        when(masterKeyProvider.needsRecoveryKeySetup()).thenReturn(true);
        when(masterKeyProvider.isKeyMismatch()).thenReturn(false);
        when(masterKeyProvider.getCurrentKeyVersion()).thenReturn(null);

        var response = controller.getSecurityStatus(testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isNeedsRecoveryKeySetup()).isTrue();
    }

    @Test
    void getSecurityStatus_shouldIndicateKeyMismatch() {
        UUID userId = UUID.randomUUID();
        when(masterKeyProvider.needsRecoveryKeySetup()).thenReturn(false);
        when(masterKeyProvider.isKeyMismatch()).thenReturn(true);
        when(masterKeyProvider.getCurrentKeyVersion()).thenReturn(2);

        var response = controller.getSecurityStatus(testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isKeyMismatch()).isTrue();
    }

    // ===== POST /recovery-key/confirm =====

    @Test
    void confirmRecoveryKeyBackup_shouldConfirmWhenValid() {
        UUID userId = UUID.randomUUID();
        String phrase = "word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12";

        when(recoveryKeyService.validate(phrase)).thenReturn(true);

        var request = new RecoveryKeyController.ConfirmBackupRequest();
        request.setRecoveryKeyPhrase(phrase);

        var response = controller.confirmRecoveryKeyBackup(request, testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(masterKeyProvider).confirmRecoveryKeyBackup(userId, phrase);
    }

    @Test
    void confirmRecoveryKeyBackup_shouldRejectInvalidKey() {
        UUID userId = UUID.randomUUID();
        String invalidPhrase = "invalid-recovery-key";

        when(recoveryKeyService.validate(invalidPhrase)).thenReturn(false);

        var request = new RecoveryKeyController.ConfirmBackupRequest();
        request.setRecoveryKeyPhrase(invalidPhrase);

        var response = controller.confirmRecoveryKeyBackup(request, testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(masterKeyProvider, never()).confirmRecoveryKeyBackup(any(), any());
    }

    // ===== POST /migrate =====

    @Test
    void migrateCredential_shouldSucceed() {
        UUID userId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();

        var request = new RecoveryKeyController.MigrateCredentialRequest();
        request.setOldRecoveryKeyPhrase("old-recovery-phrase");
        request.setCredentialId(credentialId);

        var response = controller.migrateCredential(request, testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getMessage()).contains("successfully");
        verify(masterKeyProvider).migrateWithRecoveryKey("old-recovery-phrase", credentialId, userId);
    }

    @Test
    void migrateCredential_shouldReturnBadRequestOnFailure() {
        UUID userId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();

        doThrow(new RuntimeException("Invalid recovery key"))
                .when(masterKeyProvider).migrateWithRecoveryKey(any(), any(), any());

        var request = new RecoveryKeyController.MigrateCredentialRequest();
        request.setOldRecoveryKeyPhrase("wrong-key");
        request.setCredentialId(credentialId);

        var response = controller.migrateCredential(request, testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).contains("failed");
    }

    // ===== POST /emergency-restore =====

    @Test
    void emergencyRestore_shouldSucceed() {
        UUID userId = UUID.randomUUID();
        String recoveryPhrase = "valid-recovery-phrase";
        String password = "P@ssw0rd123";

        RecoveryKey newKey = RecoveryKey.builder()
                .words(List.of("new", "word1", "word2", "word3", "word4", "word5", "word6", "word7"))
                .keyHash("hash-value")
                .keyVersion(2)
                .build();

        when(recoveryKeyService.validate(recoveryPhrase)).thenReturn(true);
        when(masterKeyProvider.emergencyRestore(recoveryPhrase, password, userId)).thenReturn(newKey);

        var request = new RecoveryKeyController.EmergencyRestoreRequest();
        request.setRecoveryKeyPhrase(recoveryPhrase);
        request.setPermanentPassword(password);

        var response = controller.emergencyRestore(request, testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getNewRecoveryKey()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("successful");
    }

    @Test
    void emergencyRestore_shouldRejectInvalidRecoveryKey() {
        UUID userId = UUID.randomUUID();
        String invalidPhrase = "invalid-phrase";

        when(recoveryKeyService.validate(invalidPhrase)).thenReturn(false);

        var request = new RecoveryKeyController.EmergencyRestoreRequest();
        request.setRecoveryKeyPhrase(invalidPhrase);
        request.setPermanentPassword("password");

        var response = controller.emergencyRestore(request, testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).contains("Invalid recovery key");
        verify(masterKeyProvider, never()).emergencyRestore(any(), any(), any());
    }

    @Test
    void emergencyRestore_shouldReturnBadRequestOnException() {
        UUID userId = UUID.randomUUID();
        String recoveryPhrase = "valid-phrase";

        when(recoveryKeyService.validate(recoveryPhrase)).thenReturn(true);
        when(masterKeyProvider.emergencyRestore(any(), any(), any()))
                .thenThrow(new RuntimeException("Restore failed"));

        var request = new RecoveryKeyController.EmergencyRestoreRequest();
        request.setRecoveryKeyPhrase(recoveryPhrase);
        request.setPermanentPassword("password");

        var response = controller.emergencyRestore(request, testUser(userId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().isSuccess()).isFalse();
    }

    // ===== DTO Tests =====

    @Test
    void securityStatusResponse_shouldBuildCorrectly() {
        var status = RecoveryKeyController.SecurityStatusResponse.builder()
                .needsRecoveryKeySetup(true)
                .keyMismatch(false)
                .currentKeyVersion(3)
                .build();

        assertThat(status.isNeedsRecoveryKeySetup()).isTrue();
        assertThat(status.isKeyMismatch()).isFalse();
        assertThat(status.getCurrentKeyVersion()).isEqualTo(3);
    }

    @Test
    void migrateResponse_shouldBuildWithRecoveryKey() {
        RecoveryKey key = RecoveryKey.builder()
                .words(List.of("word1", "word2", "word3", "word4", "word5", "word6", "word7", "word8"))
                .keyHash("hash")
                .keyVersion(1)
                .build();
        var response = RecoveryKeyController.MigrateResponse.builder()
                .success(true)
                .message("Migrated")
                .newRecoveryKey(key)
                .build();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getNewRecoveryKey()).isNotNull();
        assertThat(response.getNewRecoveryKey().getWordCount()).isEqualTo(8);
    }
}
