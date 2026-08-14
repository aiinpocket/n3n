package com.aiinpocket.n3n.webhook.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.dto.ExecutionResponse;
import com.aiinpocket.n3n.execution.service.ExecutionService;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.aiinpocket.n3n.flow.service.FlowShareService;
import com.aiinpocket.n3n.webhook.dto.CreateWebhookRequest;
import com.aiinpocket.n3n.webhook.dto.UpdateWebhookRequest;
import com.aiinpocket.n3n.webhook.dto.WebhookResponse;
import com.aiinpocket.n3n.webhook.entity.Webhook;
import com.aiinpocket.n3n.webhook.repository.WebhookRepository;
import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WebhookServiceTest extends BaseServiceTest {

    @Mock
    private WebhookRepository webhookRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExecutionService executionService;

    @Mock
    private FlowVersionRepository flowVersionRepository;

    @Mock
    private FlowShareService flowShareService;

    @InjectMocks
    private WebhookService webhookService;

    // ========== List Tests ==========

    @Test
    void listWebhooks_validUserId_returnsWebhooks() {
        // Given
        UUID userId = UUID.randomUUID();
        Webhook webhook = createTestWebhook();
        ReflectionTestUtils.setField(webhookService, "baseUrl", "http://localhost:8080");

        when(webhookRepository.findByCreatedByOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(webhook));

        // When
        List<WebhookResponse> result = webhookService.listWebhooks(userId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo(webhook.getName());
    }

    @Test
    void listWebhooksForFlow_validFlowId_returnsWebhooks() {
        // Given
        UUID flowId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Webhook webhook = createTestWebhook();
        webhook.setFlowId(flowId);
        webhook.setCreatedBy(userId);
        ReflectionTestUtils.setField(webhookService, "baseUrl", "http://localhost:8080");

        when(flowShareService.hasAccess(flowId, userId)).thenReturn(true);
        when(webhookRepository.findByFlowIdAndCreatedByOrderByCreatedAtDesc(flowId, userId))
                .thenReturn(List.of(webhook));

        // When
        List<WebhookResponse> result = webhookService.listWebhooksForFlow(flowId, userId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFlowId()).isEqualTo(flowId);
    }

    @Test
    void listWebhooksForFlow_noAccess_throwsAccessDenied() {
        // Given
        UUID flowId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(flowShareService.hasAccess(flowId, userId)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> webhookService.listWebhooksForFlow(flowId, userId))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    // ========== Get Tests ==========

    @Test
    void getWebhook_existingId_returnsWebhook() {
        // Given
        Webhook webhook = createTestWebhook();
        ReflectionTestUtils.setField(webhookService, "baseUrl", "http://localhost:8080");

        when(webhookRepository.findById(webhook.getId())).thenReturn(Optional.of(webhook));

        // When
        WebhookResponse result = webhookService.getWebhook(webhook.getId(), webhook.getCreatedBy());

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(webhook.getName());
    }

    @Test
    void getWebhook_nonExistingId_throwsException() {
        // Given
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(webhookRepository.findById(id)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> webhookService.getWebhook(id, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Webhook not found");
    }

    // ========== Namespace Tests ==========

    @Test
    void resolveWebhookNs_missing_generatesAndPersistsRandomSlug() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByWebhookNs(any())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String ns = webhookService.resolveWebhookNs(userId);

        assertThat(ns).matches("[a-z0-9]{8}");
        assertThat(user.getWebhookNs()).isEqualTo(ns);
        verify(userRepository).save(user);
    }

    @Test
    void resolveWebhookNs_existing_returnsStoredSlugWithoutSaving() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.of(
                User.builder().id(userId).webhookNs("keepns01").build()));

        assertThat(webhookService.resolveWebhookNs(userId)).isEqualTo("keepns01");
        verify(userRepository, never()).save(any());
    }

    // ========== Create Tests ==========

    @Test
    void createWebhook_validRequest_createsWebhook() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        CreateWebhookRequest request = new CreateWebhookRequest();
        request.setFlowId(flowId);
        request.setName("Test Webhook");
        request.setPath("test-hook");
        request.setMethod("POST");

        ReflectionTestUtils.setField(webhookService, "baseUrl", "http://localhost:8080");

        when(flowShareService.getUserPermission(flowId, userId)).thenReturn("owner");
        when(userRepository.findById(userId)).thenReturn(Optional.of(
                User.builder().id(userId).webhookNs("testns00").build()));
        when(webhookRepository.existsByNsAndPathAndMethod("testns00", "test-hook", "POST")).thenReturn(false);
        when(webhookRepository.save(any(Webhook.class))).thenAnswer(invocation -> {
            Webhook w = invocation.getArgument(0);
            w.setId(UUID.randomUUID());
            return w;
        });

        // When
        WebhookResponse result = webhookService.createWebhook(request, userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Test Webhook");
        verify(webhookRepository).save(any(Webhook.class));
    }

    @Test
    void createWebhook_noFlowAccess_throwsAccessDenied() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        CreateWebhookRequest request = new CreateWebhookRequest();
        request.setFlowId(flowId);
        request.setName("Test Webhook");
        request.setPath("test-hook");

        when(flowShareService.getUserPermission(flowId, userId)).thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> webhookService.createWebhook(request, userId))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(webhookRepository, never()).save(any());
    }

    @Test
    void createWebhook_viewOnlyAccess_throwsAccessDenied() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        CreateWebhookRequest request = new CreateWebhookRequest();
        request.setFlowId(flowId);
        request.setName("Test Webhook");
        request.setPath("test-hook");

        when(flowShareService.getUserPermission(flowId, userId)).thenReturn("view");

        // When/Then
        assertThatThrownBy(() -> webhookService.createWebhook(request, userId))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(webhookRepository, never()).save(any());
    }

    @Test
    void createWebhook_duplicatePath_throwsException() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        CreateWebhookRequest request = new CreateWebhookRequest();
        request.setFlowId(flowId);
        request.setName("Test Webhook");
        request.setPath("existing-path");

        when(flowShareService.getUserPermission(flowId, userId)).thenReturn("owner");
        when(userRepository.findById(userId)).thenReturn(Optional.of(
                User.builder().id(userId).webhookNs("testns00").build()));
        when(webhookRepository.existsByNsAndPathAndMethod("testns00", "existing-path", "POST")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> webhookService.createWebhook(request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createWebhook_nullMethod_defaultsToPost() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        CreateWebhookRequest request = new CreateWebhookRequest();
        request.setFlowId(flowId);
        request.setName("Test Webhook");
        request.setPath("test-hook");
        request.setMethod(null);

        ReflectionTestUtils.setField(webhookService, "baseUrl", "http://localhost:8080");

        when(flowShareService.getUserPermission(flowId, userId)).thenReturn("edit");
        when(userRepository.findById(userId)).thenReturn(Optional.of(
                User.builder().id(userId).webhookNs("testns00").build()));
        when(webhookRepository.existsByNsAndPathAndMethod("testns00", "test-hook", "POST")).thenReturn(false);
        when(webhookRepository.save(any(Webhook.class))).thenAnswer(invocation -> {
            Webhook w = invocation.getArgument(0);
            w.setId(UUID.randomUUID());
            return w;
        });

        // When
        WebhookResponse result = webhookService.createWebhook(request, userId);

        // Then
        assertThat(result).isNotNull();
        verify(webhookRepository).save(argThat(w -> "POST".equals(w.getMethod())));
    }

    // ========== Update Tests ==========

    @Test
    void updateWebhook_validRequest_updatesName() {
        // Given
        Webhook webhook = createTestWebhook();
        UUID ownerId = webhook.getCreatedBy();
        UpdateWebhookRequest request = new UpdateWebhookRequest();
        request.setName("Updated Name");

        ReflectionTestUtils.setField(webhookService, "baseUrl", "http://localhost:8080");

        when(webhookRepository.findById(webhook.getId())).thenReturn(Optional.of(webhook));
        when(webhookRepository.save(any(Webhook.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        WebhookResponse result = webhookService.updateWebhook(webhook.getId(), request, ownerId);

        // Then
        assertThat(result.getName()).isEqualTo("Updated Name");
        verify(webhookRepository).save(argThat(w -> "Updated Name".equals(w.getName())));
    }

    @Test
    void updateWebhook_updateAuthType_updatesAuthFields() {
        // Given
        Webhook webhook = createTestWebhook();
        UUID ownerId = webhook.getCreatedBy();
        UpdateWebhookRequest request = new UpdateWebhookRequest();
        request.setAuthType("apiKey");
        request.setAuthConfig(Map.of("headerName", "X-API-Key", "apiKey", "secret123"));

        ReflectionTestUtils.setField(webhookService, "baseUrl", "http://localhost:8080");

        when(webhookRepository.findById(webhook.getId())).thenReturn(Optional.of(webhook));
        when(webhookRepository.save(any(Webhook.class))).thenAnswer(inv -> inv.getArgument(0));

        // When
        WebhookResponse result = webhookService.updateWebhook(webhook.getId(), request, ownerId);

        // Then
        verify(webhookRepository).save(argThat(w ->
            "apiKey".equals(w.getAuthType()) && w.getAuthConfig() != null
        ));
    }

    @Test
    void updateWebhook_notOwner_throwsException() {
        // Given
        Webhook webhook = createTestWebhook();
        UUID otherUser = UUID.randomUUID();
        UpdateWebhookRequest request = new UpdateWebhookRequest();
        request.setName("Hacked");

        when(webhookRepository.findById(webhook.getId())).thenReturn(Optional.of(webhook));

        // When/Then
        assertThatThrownBy(() -> webhookService.updateWebhook(webhook.getId(), request, otherUser))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void updateWebhook_nonExisting_throwsException() {
        // Given
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UpdateWebhookRequest request = new UpdateWebhookRequest();
        request.setName("test");

        when(webhookRepository.findById(id)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> webhookService.updateWebhook(id, request, userId))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== Activate/Deactivate Tests ==========

    @Test
    void activateWebhook_existingWebhook_setsActive() {
        // Given
        Webhook webhook = createTestWebhook();
        webhook.setIsActive(false);
        ReflectionTestUtils.setField(webhookService, "baseUrl", "http://localhost:8080");

        when(webhookRepository.findById(webhook.getId())).thenReturn(Optional.of(webhook));
        when(webhookRepository.save(any(Webhook.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        WebhookResponse result = webhookService.activateWebhook(webhook.getId(), webhook.getCreatedBy());

        // Then
        assertThat(result.isActive()).isTrue();
        verify(webhookRepository).save(argThat(Webhook::getIsActive));
    }

    @Test
    void deactivateWebhook_existingWebhook_setsInactive() {
        // Given
        Webhook webhook = createTestWebhook();
        webhook.setIsActive(true);
        ReflectionTestUtils.setField(webhookService, "baseUrl", "http://localhost:8080");

        when(webhookRepository.findById(webhook.getId())).thenReturn(Optional.of(webhook));
        when(webhookRepository.save(any(Webhook.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        WebhookResponse result = webhookService.deactivateWebhook(webhook.getId(), webhook.getCreatedBy());

        // Then
        assertThat(result.isActive()).isFalse();
    }

    @Test
    void activateWebhook_nonExistingId_throwsException() {
        // Given
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(webhookRepository.findById(id)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> webhookService.activateWebhook(id, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== Delete Tests ==========

    @Test
    void deleteWebhook_existingWebhook_deletes() {
        // Given
        Webhook webhook = createTestWebhook();
        when(webhookRepository.findById(webhook.getId())).thenReturn(Optional.of(webhook));

        // When
        webhookService.deleteWebhook(webhook.getId(), webhook.getCreatedBy());

        // Then
        verify(webhookRepository).deleteById(webhook.getId());
    }

    @Test
    void deleteWebhook_nonExistingId_throwsException() {
        // Given
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(webhookRepository.findById(id)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> webhookService.deleteWebhook(id, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== Trigger Tests ==========

    @Test
    void triggerWebhook_activeWebhookWithPublishedVersion_triggersExecution() {
        // Given
        Webhook webhook = createTestWebhook();
        webhook.setIsActive(true);
        webhook.setAuthType("none"); // explicit opt-out of auth (null authType is now rejected)
        UUID flowId = webhook.getFlowId();
        Map<String, Object> payload = Map.of("data", "test");

        FlowVersion publishedVersion = FlowVersion.builder()
                .id(UUID.randomUUID())
                .flowId(flowId)
                .version("1.0.0")
                .status("published")
                .build();

        ExecutionResponse executionResponse = ExecutionResponse.builder()
                .id(UUID.randomUUID())
                .build();

        when(webhookRepository.findByNsIsNullAndPathAndMethodAndIsActiveTrue("test-path", "POST"))
                .thenReturn(Optional.of(webhook));
        when(flowVersionRepository.findByFlowIdAndStatus(flowId, "published"))
                .thenReturn(Optional.of(publishedVersion));
        when(executionService.createExecution(any(), any())).thenReturn(executionResponse);

        // When
        UUID executionId = webhookService.triggerWebhook("test-path", "POST", payload, null);

        // Then
        assertThat(executionId).isNotNull();
        verify(executionService).createExecution(any(), eq(webhook.getCreatedBy()));
    }

    @Test
    void triggerWebhook_inactiveWebhook_throwsException() {
        // Given
        when(webhookRepository.findByNsIsNullAndPathAndMethodAndIsActiveTrue("test-path", "POST"))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> webhookService.triggerWebhook("test-path", "POST", Map.of(), null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found or inactive");
    }

    @Test
    void triggerWebhook_noPublishedVersion_throwsException() {
        // Given
        Webhook webhook = createTestWebhook();
        webhook.setIsActive(true);
        webhook.setAuthType("none"); // explicit opt-out of auth (null authType is now rejected)

        when(webhookRepository.findByNsIsNullAndPathAndMethodAndIsActiveTrue("test-path", "POST"))
                .thenReturn(Optional.of(webhook));
        when(flowVersionRepository.findByFlowIdAndStatus(webhook.getFlowId(), "published"))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> webhookService.triggerWebhook("test-path", "POST", Map.of(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No published version");
    }

    @Test
    void triggerWebhook_nullAuthType_throwsSecurityException() {
        // Given: a webhook with no configured auth type must not be silently unauthenticated
        Webhook webhook = createTestWebhook();
        webhook.setIsActive(true);
        webhook.setAuthType(null);

        when(webhookRepository.findByNsIsNullAndPathAndMethodAndIsActiveTrue("test-path", "POST"))
                .thenReturn(Optional.of(webhook));

        // When/Then
        assertThatThrownBy(() -> webhookService.triggerWebhook("test-path", "POST", Map.of(), null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void triggerWebhook_hmacAuthMissingSignature_throwsSecurityException() {
        // Given
        Webhook webhook = createTestWebhook();
        webhook.setAuthType("hmac");
        webhook.setAuthConfig(Map.of("secret", "my-secret"));
        webhook.setIsActive(true);

        when(webhookRepository.findByNsIsNullAndPathAndMethodAndIsActiveTrue("test-path", "POST"))
                .thenReturn(Optional.of(webhook));

        // When/Then
        assertThatThrownBy(() -> webhookService.triggerWebhook("test-path", "POST", Map.of(), null))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Missing signature");
    }

    @Test
    void triggerWebhook_hmacAuthInvalidSignature_throwsSecurityException() {
        // Given
        Webhook webhook = createTestWebhook();
        webhook.setAuthType("hmac");
        webhook.setAuthConfig(Map.of("secret", "my-secret"));
        webhook.setIsActive(true);

        when(webhookRepository.findByNsIsNullAndPathAndMethodAndIsActiveTrue("test-path", "POST"))
                .thenReturn(Optional.of(webhook));

        // When/Then
        assertThatThrownBy(() -> webhookService.triggerWebhook("test-path", "POST", Map.of(), "invalid-signature"))
                .isInstanceOf(SecurityException.class);
    }

    // ========== Helper Methods ==========

    private Webhook createTestWebhook() {
        return Webhook.builder()
                .id(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .name("Test Webhook")
                .path("test-path")
                .method("POST")
                .isActive(true)
                .createdBy(UUID.randomUUID())
                .createdAt(Instant.now())
                .build();
    }
}
