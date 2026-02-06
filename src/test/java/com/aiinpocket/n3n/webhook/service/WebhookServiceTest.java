package com.aiinpocket.n3n.webhook.service;

import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.dto.ExecutionResponse;
import com.aiinpocket.n3n.execution.service.ExecutionService;
import com.aiinpocket.n3n.flow.entity.FlowVersion;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.aiinpocket.n3n.webhook.dto.CreateWebhookRequest;
import com.aiinpocket.n3n.webhook.dto.WebhookResponse;
import com.aiinpocket.n3n.webhook.entity.Webhook;
import com.aiinpocket.n3n.webhook.repository.WebhookRepository;
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
    private ExecutionService executionService;

    @Mock
    private FlowVersionRepository flowVersionRepository;

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
        Webhook webhook = createTestWebhook();
        webhook.setFlowId(flowId);
        ReflectionTestUtils.setField(webhookService, "baseUrl", "http://localhost:8080");

        when(webhookRepository.findByFlowIdOrderByCreatedAtDesc(flowId))
                .thenReturn(List.of(webhook));

        // When
        List<WebhookResponse> result = webhookService.listWebhooksForFlow(flowId);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFlowId()).isEqualTo(flowId);
    }

    // ========== Get Tests ==========

    @Test
    void getWebhook_existingId_returnsWebhook() {
        // Given
        Webhook webhook = createTestWebhook();
        ReflectionTestUtils.setField(webhookService, "baseUrl", "http://localhost:8080");

        when(webhookRepository.findById(webhook.getId())).thenReturn(Optional.of(webhook));

        // When
        WebhookResponse result = webhookService.getWebhook(webhook.getId());

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(webhook.getName());
    }

    @Test
    void getWebhook_nonExistingId_throwsException() {
        // Given
        UUID id = UUID.randomUUID();
        when(webhookRepository.findById(id)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> webhookService.getWebhook(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Webhook not found");
    }

    // ========== Create Tests ==========

    @Test
    void createWebhook_validRequest_createsWebhook() {
        // Given
        UUID userId = UUID.randomUUID();
        CreateWebhookRequest request = new CreateWebhookRequest();
        request.setFlowId(UUID.randomUUID());
        request.setName("Test Webhook");
        request.setPath("test-hook");
        request.setMethod("POST");

        ReflectionTestUtils.setField(webhookService, "baseUrl", "http://localhost:8080");

        when(webhookRepository.existsByPath("test-hook")).thenReturn(false);
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
    void createWebhook_duplicatePath_throwsException() {
        // Given
        UUID userId = UUID.randomUUID();
        CreateWebhookRequest request = new CreateWebhookRequest();
        request.setFlowId(UUID.randomUUID());
        request.setName("Test Webhook");
        request.setPath("existing-path");

        when(webhookRepository.existsByPath("existing-path")).thenReturn(true);

        // When/Then
        assertThatThrownBy(() -> webhookService.createWebhook(request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void createWebhook_nullMethod_defaultsToPost() {
        // Given
        UUID userId = UUID.randomUUID();
        CreateWebhookRequest request = new CreateWebhookRequest();
        request.setFlowId(UUID.randomUUID());
        request.setName("Test Webhook");
        request.setPath("test-hook");
        request.setMethod(null);

        ReflectionTestUtils.setField(webhookService, "baseUrl", "http://localhost:8080");

        when(webhookRepository.existsByPath("test-hook")).thenReturn(false);
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
        WebhookResponse result = webhookService.activateWebhook(webhook.getId());

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
        WebhookResponse result = webhookService.deactivateWebhook(webhook.getId());

        // Then
        assertThat(result.isActive()).isFalse();
    }

    @Test
    void activateWebhook_nonExistingId_throwsException() {
        // Given
        UUID id = UUID.randomUUID();
        when(webhookRepository.findById(id)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> webhookService.activateWebhook(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== Delete Tests ==========

    @Test
    void deleteWebhook_existingWebhook_deletes() {
        // Given
        UUID id = UUID.randomUUID();
        when(webhookRepository.existsById(id)).thenReturn(true);

        // When
        webhookService.deleteWebhook(id);

        // Then
        verify(webhookRepository).deleteById(id);
    }

    @Test
    void deleteWebhook_nonExistingId_throwsException() {
        // Given
        UUID id = UUID.randomUUID();
        when(webhookRepository.existsById(id)).thenReturn(false);

        // When/Then
        assertThatThrownBy(() -> webhookService.deleteWebhook(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ========== Trigger Tests ==========

    @Test
    void triggerWebhook_activeWebhookWithPublishedVersion_triggersExecution() {
        // Given
        Webhook webhook = createTestWebhook();
        webhook.setIsActive(true);
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

        when(webhookRepository.findByPathAndMethodAndIsActiveTrue("test-path", "POST"))
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
        when(webhookRepository.findByPathAndMethodAndIsActiveTrue("test-path", "POST"))
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

        when(webhookRepository.findByPathAndMethodAndIsActiveTrue("test-path", "POST"))
                .thenReturn(Optional.of(webhook));
        when(flowVersionRepository.findByFlowIdAndStatus(webhook.getFlowId(), "published"))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> webhookService.triggerWebhook("test-path", "POST", Map.of(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No published version");
    }

    @Test
    void triggerWebhook_hmacAuthMissingSignature_throwsSecurityException() {
        // Given
        Webhook webhook = createTestWebhook();
        webhook.setAuthType("hmac");
        webhook.setAuthConfig(Map.of("secret", "my-secret"));
        webhook.setIsActive(true);

        when(webhookRepository.findByPathAndMethodAndIsActiveTrue("test-path", "POST"))
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

        when(webhookRepository.findByPathAndMethodAndIsActiveTrue("test-path", "POST"))
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
