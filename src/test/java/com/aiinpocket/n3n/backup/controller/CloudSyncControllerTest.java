package com.aiinpocket.n3n.backup.controller;

import com.aiinpocket.n3n.auth.security.IpRateLimiter;
import com.aiinpocket.n3n.backup.dto.request.ImportSyncRequest;
import com.aiinpocket.n3n.backup.dto.request.ScanRemoteRequest;
import com.aiinpocket.n3n.backup.dto.response.CloudSyncImportResult;
import com.aiinpocket.n3n.backup.dto.response.CloudSyncManifest;
import com.aiinpocket.n3n.backup.dto.response.CloudSyncStatus;
import com.aiinpocket.n3n.backup.service.CloudSyncService;
import jakarta.servlet.http.HttpServletRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CloudSyncControllerTest {

    @Mock
    private CloudSyncService cloudSyncService;

    @Mock
    private IpRateLimiter ipRateLimiter;

    @InjectMocks
    private CloudSyncController cloudSyncController;

    private HttpServletRequest mockHttpRequest() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        lenient().when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        return req;
    }

    private UserDetails testUser() {
        return User.withUsername(UUID.randomUUID().toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    @Test
    void scan_returnsManifest() {
        var manifest = CloudSyncManifest.builder()
                .fingerprint("abcdef1234567890")
                .flowCount(3)
                .credentialCount(2)
                .aiProviderCount(1)
                .entities(List.of())
                .build();
        when(cloudSyncService.listRemoteEntities("test phrase")).thenReturn(manifest);

        var request = new ScanRemoteRequest();
        request.setRecoveryKeyPhrase("test phrase");

        var result = cloudSyncController.scan(request, mockHttpRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getFlowCount()).isEqualTo(3);
        assertThat(result.getBody().getCredentialCount()).isEqualTo(2);
        assertThat(result.getBody().getFingerprint()).isEqualTo("abcdef1234567890");
    }

    @Test
    void importEntities_returnsResult() {
        var importResult = CloudSyncImportResult.builder()
                .flowsImported(5)
                .credentialsImported(3)
                .aiProvidersImported(1)
                .skipped(2)
                .failed(0)
                .errors(List.of())
                .build();
        when(cloudSyncService.importFromRecoveryKey(eq("test phrase"), any(UUID.class)))
                .thenReturn(importResult);

        var request = new ImportSyncRequest();
        request.setRecoveryKeyPhrase("test phrase");

        var result = cloudSyncController.importEntities(request, testUser(), mockHttpRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getFlowsImported()).isEqualTo(5);
        assertThat(result.getBody().getCredentialsImported()).isEqualTo(3);
        assertThat(result.getBody().getSkipped()).isEqualTo(2);
    }

    @Test
    void getStatus_returnsStatus() {
        var status = CloudSyncStatus.builder()
                .enabled(true)
                .provider("gcs")
                .fingerprint("1234567890abcdef")
                .build();
        when(cloudSyncService.getSyncStatus()).thenReturn(status);

        var result = cloudSyncController.getStatus();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isEnabled()).isTrue();
        assertThat(result.getBody().getProvider()).isEqualTo("gcs");
    }
}
