package com.aiinpocket.n3n.credential.controller;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import com.aiinpocket.n3n.credential.dto.ConnectionTestResult;
import com.aiinpocket.n3n.credential.dto.CreateCredentialRequest;
import com.aiinpocket.n3n.credential.dto.CredentialResponse;
import com.aiinpocket.n3n.credential.dto.TestCredentialRequest;
import com.aiinpocket.n3n.credential.dto.UpdateCredentialRequest;
import com.aiinpocket.n3n.credential.entity.CredentialType;
import com.aiinpocket.n3n.credential.service.ConnectionTestService;
import com.aiinpocket.n3n.credential.service.CredentialService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CredentialControllerTest {

    @Mock
    private CredentialService credentialService;

    @Mock
    private ConnectionTestService connectionTestService;

    @Mock
    private ActivityService activityService;

    @Mock
    private com.aiinpocket.n3n.auth.security.IpRateLimiter ipRateLimiter;

    @InjectMocks
    private CredentialController credentialController;

    private UserDetails testUser() {
        return User.withUsername(UUID.randomUUID().toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private HttpServletRequest mockHttpRequest() {
        HttpServletRequest req = mock(HttpServletRequest.class);
        lenient().when(req.getRemoteAddr()).thenReturn("127.0.0.1");
        return req;
    }

    private CredentialResponse sampleCredentialResponse() {
        return CredentialResponse.builder()
                .id(UUID.randomUUID())
                .name("test-credential")
                .type("postgres")
                .description("Test credential description")
                .ownerId(UUID.randomUUID())
                .visibility("private")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ===== listCredentials (GET /api/credentials) =====

    @Test
    void listCredentials_onlyMineFalse_returnsAllAccessible() {
        var user = testUser();
        var credential = sampleCredentialResponse();
        Page<CredentialResponse> page = new PageImpl<>(List.of(credential));
        when(credentialService.listCredentials(any(UUID.class), any(Pageable.class))).thenReturn(page);

        var result = credentialController.listCredentials(false, Pageable.unpaged(), user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        assertThat(result.getBody().getContent().get(0).getName()).isEqualTo("test-credential");
        verify(credentialService).listCredentials(any(UUID.class), any(Pageable.class));
        verify(credentialService, never()).listMyCredentials(any(), any());
    }

    @Test
    void listCredentials_onlyMineTrue_returnsOnlyMine() {
        var user = testUser();
        Page<CredentialResponse> page = new PageImpl<>(List.of());
        when(credentialService.listMyCredentials(any(UUID.class), any(Pageable.class))).thenReturn(page);

        var result = credentialController.listCredentials(true, Pageable.unpaged(), user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isZero();
        verify(credentialService).listMyCredentials(any(UUID.class), any(Pageable.class));
        verify(credentialService, never()).listCredentials(any(), any());
    }

    @Test
    void listCredentials_emptyPage_returnsOk() {
        var user = testUser();
        when(credentialService.listCredentials(any(UUID.class), any(Pageable.class))).thenReturn(Page.empty());

        var result = credentialController.listCredentials(false, Pageable.unpaged(), user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isZero();
    }

    // ===== listMyCredentials (GET /api/credentials/mine) =====

    @Test
    void listMyCredentials_returnsPage() {
        var user = testUser();
        var credential = sampleCredentialResponse();
        Page<CredentialResponse> page = new PageImpl<>(List.of(credential));
        when(credentialService.listMyCredentials(any(UUID.class), any(Pageable.class))).thenReturn(page);

        var result = credentialController.listMyCredentials(Pageable.unpaged(), user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getTotalElements()).isEqualTo(1);
        assertThat(result.getBody().getContent().get(0).getType()).isEqualTo("postgres");
    }

    @Test
    void listMyCredentials_emptyPage_returnsOk() {
        var user = testUser();
        when(credentialService.listMyCredentials(any(UUID.class), any(Pageable.class))).thenReturn(Page.empty());

        var result = credentialController.listMyCredentials(Pageable.unpaged(), user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isZero();
    }

    // ===== getCredential (GET /api/credentials/{id}) =====

    @Test
    void getCredential_found_returnsOk() {
        var user = testUser();
        var credentialId = UUID.randomUUID();
        var credential = CredentialResponse.builder()
                .id(credentialId)
                .name("my-cred")
                .type("redis")
                .build();
        when(credentialService.getCredential(eq(credentialId), any(UUID.class))).thenReturn(credential);

        var result = credentialController.getCredential(credentialId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getId()).isEqualTo(credentialId);
        assertThat(result.getBody().getName()).isEqualTo("my-cred");
    }

    @Test
    void getCredential_notFound_throwsException() {
        var user = testUser();
        var credentialId = UUID.randomUUID();
        when(credentialService.getCredential(eq(credentialId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Credential not found: " + credentialId));

        assertThatThrownBy(() -> credentialController.getCredential(credentialId, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Credential not found");
    }

    // ===== createCredential (POST /api/credentials) =====

    @Test
    void createCredential_success_returnsCreated() {
        var user = testUser();
        var request = new CreateCredentialRequest();
        request.setName("new-cred");
        request.setType("mongodb");
        request.setDescription("MongoDB credential");
        request.setData(Map.of("host", "localhost", "port", 27017));

        var response = CredentialResponse.builder()
                .id(UUID.randomUUID())
                .name("new-cred")
                .type("mongodb")
                .description("MongoDB credential")
                .build();
        when(credentialService.createCredential(any(CreateCredentialRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = credentialController.createCredential(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("new-cred");
        assertThat(result.getBody().getType()).isEqualTo("mongodb");
        verify(activityService).logCredentialCreate(
                any(UUID.class), eq(response.getId()), eq("new-cred"), eq("mongodb"));
    }

    @Test
    void createCredential_duplicateName_throwsException() {
        var user = testUser();
        var request = new CreateCredentialRequest();
        request.setName("existing-cred");
        request.setType("postgres");
        request.setData(Map.of("host", "localhost"));

        when(credentialService.createCredential(any(CreateCredentialRequest.class), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("Credential with this name already exists"));

        assertThatThrownBy(() -> credentialController.createCredential(request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
        verify(activityService, never()).logCredentialCreate(any(), any(), any(), any());
    }

    @Test
    void createCredential_invalidType_throwsException() {
        var user = testUser();
        var request = new CreateCredentialRequest();
        request.setName("bad-type-cred");
        request.setType("nonexistent");
        request.setData(Map.of("key", "value"));

        when(credentialService.createCredential(any(CreateCredentialRequest.class), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("Invalid credential type: nonexistent"));

        assertThatThrownBy(() -> credentialController.createCredential(request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid credential type");
    }

    // ===== updateCredential (PUT /api/credentials/{id}) =====

    @Test
    void updateCredential_success_returnsOk() {
        var user = testUser();
        var credentialId = UUID.randomUUID();
        var request = new UpdateCredentialRequest();
        request.setDescription("Updated description");
        request.setVisibility("shared");

        var response = CredentialResponse.builder()
                .id(credentialId)
                .name("updated-cred")
                .type("postgres")
                .description("Updated description")
                .visibility("shared")
                .build();
        when(credentialService.updateCredential(eq(credentialId), any(UpdateCredentialRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = credentialController.updateCredential(credentialId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getDescription()).isEqualTo("Updated description");
        assertThat(result.getBody().getVisibility()).isEqualTo("shared");
        verify(activityService).logActivity(
                any(UUID.class),
                eq(ActivityService.CREDENTIAL_UPDATE),
                eq("credential"),
                eq(credentialId),
                eq("updated-cred"),
                isNull());
    }

    @Test
    void updateCredential_notFound_throwsException() {
        var user = testUser();
        var credentialId = UUID.randomUUID();
        var request = new UpdateCredentialRequest();
        request.setDescription("Updated");

        when(credentialService.updateCredential(eq(credentialId), any(UpdateCredentialRequest.class), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Credential not found: " + credentialId));

        assertThatThrownBy(() -> credentialController.updateCredential(credentialId, request, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Credential not found");
        verify(activityService, never()).logActivity(any(), any(), any(), any(), any(), any());
    }

    @Test
    void updateCredential_withDataUpdate_returnsOk() {
        var user = testUser();
        var credentialId = UUID.randomUUID();
        var request = new UpdateCredentialRequest();
        request.setData(Map.of("host", "new-host", "port", 5433));

        var response = CredentialResponse.builder()
                .id(credentialId)
                .name("data-updated-cred")
                .type("postgres")
                .build();
        when(credentialService.updateCredential(eq(credentialId), any(UpdateCredentialRequest.class), any(UUID.class)))
                .thenReturn(response);

        var result = credentialController.updateCredential(credentialId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        verify(activityService).logActivity(
                any(UUID.class),
                eq(ActivityService.CREDENTIAL_UPDATE),
                eq("credential"),
                eq(credentialId),
                eq("data-updated-cred"),
                isNull());
    }

    // ===== deleteCredential (DELETE /api/credentials/{id}) =====

    @Test
    void deleteCredential_success_returnsNoContent() {
        var user = testUser();
        var credentialId = UUID.randomUUID();
        var credential = CredentialResponse.builder()
                .id(credentialId)
                .name("cred-to-delete")
                .type("redis")
                .build();
        when(credentialService.getCredential(eq(credentialId), any(UUID.class))).thenReturn(credential);
        doNothing().when(credentialService).deleteCredential(eq(credentialId), any(UUID.class));

        var result = credentialController.deleteCredential(credentialId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(result.getBody()).isNull();
        verify(credentialService).getCredential(eq(credentialId), any(UUID.class));
        verify(credentialService).deleteCredential(eq(credentialId), any(UUID.class));
        verify(activityService).logCredentialDelete(any(UUID.class), eq(credentialId), eq("cred-to-delete"));
    }

    @Test
    void deleteCredential_notFound_throwsException() {
        var user = testUser();
        var credentialId = UUID.randomUUID();
        when(credentialService.getCredential(eq(credentialId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Credential not found: " + credentialId));

        assertThatThrownBy(() -> credentialController.deleteCredential(credentialId, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Credential not found");
        verify(credentialService, never()).deleteCredential(any(), any());
        verify(activityService, never()).logCredentialDelete(any(), any(), any());
    }

    @Test
    void deleteCredential_serviceDeleteFails_throwsException() {
        var user = testUser();
        var credentialId = UUID.randomUUID();
        var credential = CredentialResponse.builder()
                .id(credentialId)
                .name("cred-to-delete")
                .type("postgres")
                .build();
        when(credentialService.getCredential(eq(credentialId), any(UUID.class))).thenReturn(credential);
        doThrow(new ResourceNotFoundException("Credential not found: " + credentialId))
                .when(credentialService).deleteCredential(eq(credentialId), any(UUID.class));

        assertThatThrownBy(() -> credentialController.deleteCredential(credentialId, user))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(activityService, never()).logCredentialDelete(any(), any(), any());
    }

    // ===== getCredentialData (GET /api/credentials/{id}/data) =====

    @Test
    void getCredentialData_success_returnsDecryptedData() {
        var user = testUser();
        var credentialId = UUID.randomUUID();
        var credential = CredentialResponse.builder()
                .id(credentialId)
                .name("data-cred")
                .type("postgres")
                .build();
        Map<String, Object> decryptedData = Map.of(
                "host", "db.example.com",
                "port", 5432,
                "username", "admin",
                "password", "secret123"
        );
        when(credentialService.getCredential(eq(credentialId), any(UUID.class))).thenReturn(credential);
        when(credentialService.getDecryptedData(eq(credentialId), any(UUID.class))).thenReturn(decryptedData);

        var result = credentialController.getCredentialData(credentialId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).containsEntry("host", "db.example.com");
        assertThat(result.getBody()).containsEntry("username", "admin");
        assertThat(result.getBody()).hasSize(4);
        verify(activityService).logCredentialAccess(any(UUID.class), eq(credentialId), eq("data-cred"), eq("api"));
    }

    @Test
    void getCredentialData_notFound_throwsException() {
        var user = testUser();
        var credentialId = UUID.randomUUID();
        when(credentialService.getCredential(eq(credentialId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Credential not found: " + credentialId));

        assertThatThrownBy(() -> credentialController.getCredentialData(credentialId, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Credential not found");
        verify(credentialService, never()).getDecryptedData(any(), any());
        verify(activityService, never()).logCredentialAccess(any(), any(), any(), any());
    }

    @Test
    void getCredentialData_decryptionFails_throwsException() {
        var user = testUser();
        var credentialId = UUID.randomUUID();
        var credential = CredentialResponse.builder()
                .id(credentialId)
                .name("bad-cred")
                .type("postgres")
                .build();
        when(credentialService.getCredential(eq(credentialId), any(UUID.class))).thenReturn(credential);
        when(credentialService.getDecryptedData(eq(credentialId), any(UUID.class)))
                .thenThrow(new RuntimeException("Failed to parse credential data"));

        assertThatThrownBy(() -> credentialController.getCredentialData(credentialId, user))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to parse credential data");
    }

    // ===== listCredentialTypes (GET /api/credentials/types) =====

    @Test
    void listCredentialTypes_returnsList() {
        var type1 = CredentialType.builder()
                .id(UUID.randomUUID())
                .name("postgres")
                .displayName("PostgreSQL")
                .description("PostgreSQL database credentials")
                .icon("database")
                .build();
        var type2 = CredentialType.builder()
                .id(UUID.randomUUID())
                .name("mongodb")
                .displayName("MongoDB")
                .description("MongoDB database credentials")
                .icon("database")
                .build();
        when(credentialService.listCredentialTypes()).thenReturn(List.of(type1, type2));

        var result = credentialController.listCredentialTypes();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(2);
        assertThat(result.getBody().get(0).getName()).isEqualTo("postgres");
        assertThat(result.getBody().get(1).getName()).isEqualTo("mongodb");
    }

    @Test
    void listCredentialTypes_empty_returnsEmptyList() {
        when(credentialService.listCredentialTypes()).thenReturn(List.of());

        var result = credentialController.listCredentialTypes();

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    // ===== testConnection (POST /api/credentials/test) =====

    @Test
    void testConnection_success_returnsOk() {
        var user = testUser();
        var request = new TestCredentialRequest();
        request.setType("postgres");
        request.setData(Map.of("host", "localhost", "port", 5432, "database", "testdb"));

        var testResult = ConnectionTestResult.success("PostgreSQL connection successful", 42L, "PostgreSQL 15.2");
        when(connectionTestService.testConnection(eq("postgres"), any())).thenReturn(testResult);

        var result = credentialController.testConnection(request, user, mockHttpRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getMessage()).isEqualTo("PostgreSQL connection successful");
        assertThat(result.getBody().getLatencyMs()).isEqualTo(42L);
        assertThat(result.getBody().getServerVersion()).isEqualTo("PostgreSQL 15.2");
    }

    @Test
    void testConnection_failure_returnsOkWithFailure() {
        var user = testUser();
        var request = new TestCredentialRequest();
        request.setType("redis");
        request.setData(Map.of("host", "unreachable-host", "port", 6379));

        var testResult = ConnectionTestResult.failure("Connection test failed", 5000L);
        when(connectionTestService.testConnection(eq("redis"), any())).thenReturn(testResult);

        var result = credentialController.testConnection(request, user, mockHttpRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getMessage()).isEqualTo("Connection test failed");
        assertThat(result.getBody().getLatencyMs()).isEqualTo(5000L);
        assertThat(result.getBody().getServerVersion()).isNull();
    }

    @Test
    void testConnection_unsupportedType_returnsOkWithFailure() {
        var user = testUser();
        var request = new TestCredentialRequest();
        request.setType("unknown-db");
        request.setData(Map.of("host", "localhost"));

        var testResult = ConnectionTestResult.failure("Unsupported credential type: unknown-db", 1L);
        when(connectionTestService.testConnection(eq("unknown-db"), any())).thenReturn(testResult);

        var result = credentialController.testConnection(request, user, mockHttpRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getMessage()).contains("Unsupported");
    }

    // ===== testSavedCredential (POST /api/credentials/{id}/test) =====

    @Test
    void testSavedCredential_success_returnsOk() {
        var user = testUser();
        var credentialId = UUID.randomUUID();

        var testResult = ConnectionTestResult.success("MongoDB connection successful", 85L, "MongoDB 7.0.4");
        when(connectionTestService.testSavedCredential(eq(credentialId), any(UUID.class))).thenReturn(testResult);

        var result = credentialController.testSavedCredential(credentialId, user, mockHttpRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isTrue();
        assertThat(result.getBody().getMessage()).isEqualTo("MongoDB connection successful");
        assertThat(result.getBody().getServerVersion()).isEqualTo("MongoDB 7.0.4");
    }

    @Test
    void testSavedCredential_notFound_throwsException() {
        var user = testUser();
        var credentialId = UUID.randomUUID();
        when(connectionTestService.testSavedCredential(eq(credentialId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Credential not found"));

        assertThatThrownBy(() -> credentialController.testSavedCredential(credentialId, user, mockHttpRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Credential not found");
    }

    @Test
    void testSavedCredential_connectionFails_returnsOkWithFailure() {
        var user = testUser();
        var credentialId = UUID.randomUUID();

        var testResult = ConnectionTestResult.failure("Connection test failed", 10000L);
        when(connectionTestService.testSavedCredential(eq(credentialId), any(UUID.class))).thenReturn(testResult);

        var result = credentialController.testSavedCredential(credentialId, user, mockHttpRequest());

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isSuccess()).isFalse();
        assertThat(result.getBody().getLatencyMs()).isEqualTo(10000L);
    }

    // ===== userId extraction verification =====

    @Test
    void allEndpoints_extractUserIdFromUserDetails() {
        var userId = UUID.randomUUID();
        var user = User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();

        var credential = CredentialResponse.builder()
                .id(UUID.randomUUID())
                .name("verify-user-cred")
                .type("postgres")
                .build();
        when(credentialService.getCredential(any(UUID.class), eq(userId))).thenReturn(credential);

        credentialController.getCredential(UUID.randomUUID(), user);

        verify(credentialService).getCredential(any(UUID.class), eq(userId));
    }

    // ===== Activity logging verification =====

    @Test
    void createCredential_logsActivity() {
        var user = testUser();
        var credentialId = UUID.randomUUID();
        var request = new CreateCredentialRequest();
        request.setName("logged-cred");
        request.setType("mysql");
        request.setData(Map.of("host", "localhost"));

        var response = CredentialResponse.builder()
                .id(credentialId)
                .name("logged-cred")
                .type("mysql")
                .build();
        when(credentialService.createCredential(any(), any())).thenReturn(response);

        credentialController.createCredential(request, user);

        verify(activityService).logCredentialCreate(
                any(UUID.class), eq(credentialId), eq("logged-cred"), eq("mysql"));
    }

    @Test
    void updateCredential_logsActivity() {
        var user = testUser();
        var credentialId = UUID.randomUUID();
        var request = new UpdateCredentialRequest();
        request.setDescription("Updated");

        var response = CredentialResponse.builder()
                .id(credentialId)
                .name("updated-cred")
                .type("postgres")
                .build();
        when(credentialService.updateCredential(eq(credentialId), any(), any())).thenReturn(response);

        credentialController.updateCredential(credentialId, request, user);

        verify(activityService).logActivity(
                any(UUID.class),
                eq(ActivityService.CREDENTIAL_UPDATE),
                eq("credential"),
                eq(credentialId),
                eq("updated-cred"),
                isNull());
    }

    @Test
    void deleteCredential_logsActivity() {
        var user = testUser();
        var credentialId = UUID.randomUUID();
        var credential = CredentialResponse.builder()
                .id(credentialId)
                .name("deleted-cred")
                .type("redis")
                .build();
        when(credentialService.getCredential(eq(credentialId), any())).thenReturn(credential);
        doNothing().when(credentialService).deleteCredential(eq(credentialId), any());

        credentialController.deleteCredential(credentialId, user);

        verify(activityService).logCredentialDelete(any(UUID.class), eq(credentialId), eq("deleted-cred"));
    }

    @Test
    void getCredentialData_logsAccessActivity() {
        var user = testUser();
        var credentialId = UUID.randomUUID();
        var credential = CredentialResponse.builder()
                .id(credentialId)
                .name("accessed-cred")
                .type("postgres")
                .build();
        when(credentialService.getCredential(eq(credentialId), any())).thenReturn(credential);
        when(credentialService.getDecryptedData(eq(credentialId), any()))
                .thenReturn(Map.of("host", "localhost"));

        credentialController.getCredentialData(credentialId, user);

        verify(activityService).logCredentialAccess(
                any(UUID.class), eq(credentialId), eq("accessed-cred"), eq("api"));
    }

    // ===== Multiple credentials in page =====

    @Test
    void listCredentials_multipleItems_returnsAll() {
        var user = testUser();
        var cred1 = CredentialResponse.builder().id(UUID.randomUUID()).name("cred-1").type("postgres").build();
        var cred2 = CredentialResponse.builder().id(UUID.randomUUID()).name("cred-2").type("mongodb").build();
        var cred3 = CredentialResponse.builder().id(UUID.randomUUID()).name("cred-3").type("redis").build();
        Page<CredentialResponse> page = new PageImpl<>(List.of(cred1, cred2, cred3));
        when(credentialService.listCredentials(any(UUID.class), any(Pageable.class))).thenReturn(page);

        var result = credentialController.listCredentials(false, Pageable.unpaged(), user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getTotalElements()).isEqualTo(3);
        assertThat(result.getBody().getContent())
                .extracting(CredentialResponse::getName)
                .containsExactly("cred-1", "cred-2", "cred-3");
    }
}
