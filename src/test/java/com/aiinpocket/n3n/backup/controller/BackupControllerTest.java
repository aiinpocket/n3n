package com.aiinpocket.n3n.backup.controller;

import com.aiinpocket.n3n.auth.security.IpRateLimiter;
import com.aiinpocket.n3n.backup.dto.request.ListRemoteRequest;
import com.aiinpocket.n3n.backup.dto.request.RestoreBackupRequest;
import com.aiinpocket.n3n.backup.dto.request.UpdateBackupSettingsRequest;
import com.aiinpocket.n3n.backup.dto.response.BackupHistoryResponse;
import com.aiinpocket.n3n.backup.dto.response.BackupSettingsResponse;
import com.aiinpocket.n3n.backup.dto.response.RemoteBackupInfo;
import com.aiinpocket.n3n.backup.service.BackupService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackupControllerTest {

    @Mock
    private BackupService backupService;

    @Mock
    private IpRateLimiter ipRateLimiter;

    @InjectMocks
    private BackupController backupController;

    private HttpServletRequest mockHttpRequest() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        lenient().when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        return req;
    }

    private UserDetails testUser() {
        return User.withUsername(UUID.randomUUID().toString())
                .password("test")
                .authorities("ROLE_ADMIN")
                .build();
    }

    @Test
    void getSettings_returnsOk() {
        var response = BackupSettingsResponse.builder()
                .enabled(true)
                .provider("s3")
                .bucket("test-bucket")
                .build();
        when(backupService.getSettings()).thenReturn(response);

        var result = backupController.getSettings();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getProvider()).isEqualTo("s3");
    }

    @Test
    void updateSettings_returnsOk() {
        var request = new UpdateBackupSettingsRequest();
        request.setProvider("gcs");

        var response = BackupSettingsResponse.builder()
                .enabled(true)
                .provider("gcs")
                .build();
        when(backupService.updateSettings(any())).thenReturn(response);

        var result = backupController.updateSettings(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getProvider()).isEqualTo("gcs");
    }

    @Test
    void testConnection_success_returnsOkWithTrue() {
        when(backupService.testConnection()).thenReturn(true);

        var result = backupController.testConnection(mockHttpRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getMessage()).isEqualTo("Connection successful");
    }

    @Test
    void testConnection_failure_returnsOkWithFalse() {
        when(backupService.testConnection()).thenReturn(false);

        var result = backupController.testConnection(mockHttpRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getMessage()).isEqualTo("Connection failed");
    }

    @Test
    void createBackup_returnsOk() {
        var history = BackupHistoryResponse.builder()
                .id(UUID.randomUUID())
                .filename("n3n-abc123-20260210.enc")
                .status("completed")
                .build();
        when(backupService.createBackup(any())).thenReturn(history);

        var result = backupController.createBackup(testUser(), mockHttpRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getStatus()).isEqualTo("completed");
    }

    @Test
    void getHistory_returnsPage() {
        when(backupService.getHistory(any())).thenReturn(Page.empty());

        var result = backupController.getHistory(Pageable.unpaged());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isZero();
    }

    @Test
    void listRemoteBackups_returnsBackupList() {
        var request = new ListRemoteRequest();
        request.setRecoveryKeyPhrase("word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12");

        var remote = RemoteBackupInfo.builder()
                .filename("n3n-abc-20260210.enc")
                .size(1024L)
                .lastModified(Instant.now().toString())
                .build();
        when(backupService.listRemoteBackups(any())).thenReturn(List.of(remote));

        var result = backupController.listRemoteBackups(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getFilename()).contains("n3n-abc");
    }

    @Test
    void restoreBackup_returnsOk() {
        var request = new RestoreBackupRequest();
        request.setRecoveryKeyPhrase("word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12");
        request.setFilename("n3n-abc-20260210.enc");

        doNothing().when(backupService).restoreBackup(any(), any(), any());

        var result = backupController.restoreBackup(request, testUser(), mockHttpRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getMessage()).contains("restored successfully");
        verify(backupService).restoreBackup(
                eq("word1 word2 word3 word4 word5 word6 word7 word8 word9 word10 word11 word12"),
                eq("n3n-abc-20260210.enc"),
                any(UUID.class));
    }
}
