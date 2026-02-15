package com.aiinpocket.n3n.webhook.controller;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.webhook.dto.CreateWebhookRequest;
import com.aiinpocket.n3n.webhook.dto.UpdateWebhookRequest;
import com.aiinpocket.n3n.webhook.dto.WebhookResponse;
import com.aiinpocket.n3n.webhook.service.WebhookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
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
class WebhookControllerTest {

    @Mock
    private WebhookService webhookService;

    @Mock
    private ActivityService activityService;

    @InjectMocks
    private WebhookController webhookController;

    private UserDetails testUser() {
        return User.withUsername(UUID.randomUUID().toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
    }

    private WebhookResponse sampleWebhookResponse() {
        return WebhookResponse.builder()
                .id(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .name("test-webhook")
                .path("my-webhook-path")
                .method("POST")
                .isActive(true)
                .authType("none")
                .webhookUrl("http://localhost:8080/webhook/my-webhook-path")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ===== listWebhooks (GET /api/webhooks) =====

    @Test
    void listWebhooks_returnsOkWithList() {
        var user = testUser();
        var webhook = sampleWebhookResponse();
        when(webhookService.listWebhooks(any(UUID.class))).thenReturn(List.of(webhook));

        var result = webhookController.listWebhooks(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(1);
        assertThat(result.getBody().get(0).getName()).isEqualTo("test-webhook");
        verify(webhookService).listWebhooks(any(UUID.class));
    }

    @Test
    void listWebhooks_emptyList_returnsOk() {
        var user = testUser();
        when(webhookService.listWebhooks(any(UUID.class))).thenReturn(List.of());

        var result = webhookController.listWebhooks(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).isEmpty();
    }

    @Test
    void listWebhooks_multipleWebhooks_returnsAll() {
        var user = testUser();
        var webhook1 = sampleWebhookResponse();
        var webhook2 = WebhookResponse.builder()
                .id(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .name("second-webhook")
                .path("another-path")
                .method("GET")
                .isActive(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(webhookService.listWebhooks(any(UUID.class))).thenReturn(List.of(webhook1, webhook2));

        var result = webhookController.listWebhooks(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).hasSize(2);
    }

    // ===== listWebhooksForFlow (GET /api/webhooks/flow/{flowId}) =====

    @Test
    void listWebhooksForFlow_returnsOkWithList() {
        var user = testUser();
        UUID flowId = UUID.randomUUID();
        var webhook = sampleWebhookResponse();
        when(webhookService.listWebhooksForFlow(eq(flowId), any(UUID.class))).thenReturn(List.of(webhook));

        var result = webhookController.listWebhooksForFlow(flowId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody()).hasSize(1);
        verify(webhookService).listWebhooksForFlow(eq(flowId), any(UUID.class));
    }

    @Test
    void listWebhooksForFlow_emptyList_returnsOk() {
        var user = testUser();
        UUID flowId = UUID.randomUUID();
        when(webhookService.listWebhooksForFlow(eq(flowId), any(UUID.class))).thenReturn(List.of());

        var result = webhookController.listWebhooksForFlow(flowId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEmpty();
    }

    // ===== getWebhook (GET /api/webhooks/{id}) =====

    @Test
    void getWebhook_found_returnsOk() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        var webhook = sampleWebhookResponse();
        when(webhookService.getWebhook(eq(webhookId), any(UUID.class))).thenReturn(webhook);

        var result = webhookController.getWebhook(webhookId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("test-webhook");
        assertThat(result.getBody().getPath()).isEqualTo("my-webhook-path");
        assertThat(result.getBody().getMethod()).isEqualTo("POST");
        assertThat(result.getBody().isActive()).isTrue();
        verify(webhookService).getWebhook(eq(webhookId), any(UUID.class));
    }

    @Test
    void getWebhook_notFound_throwsResourceNotFoundException() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        when(webhookService.getWebhook(eq(webhookId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Webhook not found: " + webhookId));

        assertThatThrownBy(() -> webhookController.getWebhook(webhookId, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Webhook not found");
    }

    @Test
    void getWebhook_accessDenied_throwsAccessDeniedException() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        when(webhookService.getWebhook(eq(webhookId), any(UUID.class)))
                .thenThrow(new AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> webhookController.getWebhook(webhookId, user))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Access denied");
    }

    // ===== createWebhook (POST /api/webhooks) =====

    @Test
    void createWebhook_success_returnsCreated() {
        var user = testUser();
        var request = new CreateWebhookRequest();
        request.setFlowId(UUID.randomUUID());
        request.setName("new-webhook");
        request.setPath("new-path");
        request.setMethod("POST");

        var webhook = sampleWebhookResponse();
        when(webhookService.createWebhook(eq(request), any(UUID.class))).thenReturn(webhook);

        var result = webhookController.createWebhook(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("test-webhook");
        verify(webhookService).createWebhook(eq(request), any(UUID.class));
        verify(activityService).logWebhookCreate(
                any(UUID.class), eq(webhook.getId()), eq(webhook.getPath()), eq(webhook.getFlowId()));
    }

    @Test
    void createWebhook_duplicatePath_throwsIllegalArgument() {
        var user = testUser();
        var request = new CreateWebhookRequest();
        request.setFlowId(UUID.randomUUID());
        request.setName("duplicate-webhook");
        request.setPath("existing-path");
        request.setMethod("POST");

        when(webhookService.createWebhook(eq(request), any(UUID.class)))
                .thenThrow(new IllegalArgumentException("Webhook path already exists for method POST: existing-path"));

        assertThatThrownBy(() -> webhookController.createWebhook(request, user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Webhook path already exists");
    }

    @Test
    void createWebhook_logsActivity() {
        var user = testUser();
        var request = new CreateWebhookRequest();
        request.setFlowId(UUID.randomUUID());
        request.setName("activity-webhook");
        request.setPath("activity-path");

        UUID webhookId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        var webhook = WebhookResponse.builder()
                .id(webhookId)
                .flowId(flowId)
                .name("activity-webhook")
                .path("activity-path")
                .method("POST")
                .isActive(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(webhookService.createWebhook(eq(request), any(UUID.class))).thenReturn(webhook);

        webhookController.createWebhook(request, user);

        verify(activityService).logWebhookCreate(any(UUID.class), eq(webhookId), eq("activity-path"), eq(flowId));
    }

    // ===== updateWebhook (PUT /api/webhooks/{id}) =====

    @Test
    void updateWebhook_success_returnsOk() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        var request = new UpdateWebhookRequest();
        request.setName("updated-name");

        var updated = WebhookResponse.builder()
                .id(webhookId)
                .flowId(UUID.randomUUID())
                .name("updated-name")
                .path("my-path")
                .method("POST")
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(webhookService.updateWebhook(eq(webhookId), eq(request), any(UUID.class))).thenReturn(updated);

        var result = webhookController.updateWebhook(webhookId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getName()).isEqualTo("updated-name");
        verify(webhookService).updateWebhook(eq(webhookId), eq(request), any(UUID.class));
    }

    @Test
    void updateWebhook_logsActivity() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        var request = new UpdateWebhookRequest();
        request.setName("log-test");

        var updated = WebhookResponse.builder()
                .id(webhookId)
                .flowId(UUID.randomUUID())
                .name("log-test")
                .path("log-path")
                .method("POST")
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(webhookService.updateWebhook(eq(webhookId), eq(request), any(UUID.class))).thenReturn(updated);

        webhookController.updateWebhook(webhookId, request, user);

        verify(activityService).logActivity(
                any(UUID.class), eq("WEBHOOK_UPDATE"), eq("webhook"),
                eq(webhookId), eq("log-path"), isNull());
    }

    @Test
    void updateWebhook_notFound_throwsResourceNotFoundException() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        var request = new UpdateWebhookRequest();
        request.setName("not-found");

        when(webhookService.updateWebhook(eq(webhookId), eq(request), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Webhook not found: " + webhookId));

        assertThatThrownBy(() -> webhookController.updateWebhook(webhookId, request, user))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Webhook not found");
    }

    @Test
    void updateWebhook_accessDenied_throwsAccessDeniedException() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        var request = new UpdateWebhookRequest();
        request.setName("denied");

        when(webhookService.updateWebhook(eq(webhookId), eq(request), any(UUID.class)))
                .thenThrow(new AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> webhookController.updateWebhook(webhookId, request, user))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ===== activateWebhook (POST /api/webhooks/{id}/activate) =====

    @Test
    void activateWebhook_success_returnsOk() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        var activated = WebhookResponse.builder()
                .id(webhookId)
                .flowId(UUID.randomUUID())
                .name("activated-webhook")
                .path("activate-path")
                .method("POST")
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(webhookService.activateWebhook(eq(webhookId), any(UUID.class))).thenReturn(activated);

        var result = webhookController.activateWebhook(webhookId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isActive()).isTrue();
        verify(webhookService).activateWebhook(eq(webhookId), any(UUID.class));
    }

    @Test
    void activateWebhook_notFound_throwsResourceNotFoundException() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        when(webhookService.activateWebhook(eq(webhookId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Webhook not found: " + webhookId));

        assertThatThrownBy(() -> webhookController.activateWebhook(webhookId, user))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void activateWebhook_accessDenied_throwsAccessDeniedException() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        when(webhookService.activateWebhook(eq(webhookId), any(UUID.class)))
                .thenThrow(new AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> webhookController.activateWebhook(webhookId, user))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ===== deactivateWebhook (POST /api/webhooks/{id}/deactivate) =====

    @Test
    void deactivateWebhook_success_returnsOk() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        var deactivated = WebhookResponse.builder()
                .id(webhookId)
                .flowId(UUID.randomUUID())
                .name("deactivated-webhook")
                .path("deactivate-path")
                .method("POST")
                .isActive(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(webhookService.deactivateWebhook(eq(webhookId), any(UUID.class))).thenReturn(deactivated);

        var result = webhookController.deactivateWebhook(webhookId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().isActive()).isFalse();
        verify(webhookService).deactivateWebhook(eq(webhookId), any(UUID.class));
    }

    @Test
    void deactivateWebhook_notFound_throwsResourceNotFoundException() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        when(webhookService.deactivateWebhook(eq(webhookId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Webhook not found: " + webhookId));

        assertThatThrownBy(() -> webhookController.deactivateWebhook(webhookId, user))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deactivateWebhook_accessDenied_throwsAccessDeniedException() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        when(webhookService.deactivateWebhook(eq(webhookId), any(UUID.class)))
                .thenThrow(new AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> webhookController.deactivateWebhook(webhookId, user))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ===== testWebhook (POST /api/webhooks/{id}/test) =====

    @Test
    void testWebhook_success_returnsOkWithExecutionId() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        var webhook = sampleWebhookResponse();

        when(webhookService.getWebhook(eq(webhookId), any(UUID.class))).thenReturn(webhook);
        when(webhookService.triggerWebhook(
                eq(webhook.getPath()), eq(webhook.getMethod()),
                any(), isNull(), eq(true)))
                .thenReturn(executionId);

        var result = webhookController.testWebhook(webhookId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("success")).isEqualTo(true);
        assertThat(result.getBody().get("executionId")).isEqualTo(executionId.toString());
        assertThat(result.getBody().get("message")).isEqualTo("Test webhook triggered successfully");
    }

    @Test
    void testWebhook_illegalState_returnsErrorMessage() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        var webhook = sampleWebhookResponse();

        when(webhookService.getWebhook(eq(webhookId), any(UUID.class))).thenReturn(webhook);
        when(webhookService.triggerWebhook(
                eq(webhook.getPath()), eq(webhook.getMethod()),
                any(), isNull(), eq(true)))
                .thenThrow(new IllegalStateException("No published version available for flow"));

        var result = webhookController.testWebhook(webhookId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("success")).isEqualTo(false);
        assertThat(result.getBody().get("error")).isEqualTo("No published version available for flow");
    }

    @Test
    void testWebhook_unexpectedException_returnsGenericError() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        var webhook = sampleWebhookResponse();

        when(webhookService.getWebhook(eq(webhookId), any(UUID.class))).thenReturn(webhook);
        when(webhookService.triggerWebhook(
                eq(webhook.getPath()), eq(webhook.getMethod()),
                any(), isNull(), eq(true)))
                .thenThrow(new RuntimeException("Unexpected error"));

        var result = webhookController.testWebhook(webhookId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("success")).isEqualTo(false);
        assertThat(result.getBody().get("error")).isEqualTo("Webhook test failed");
    }

    @Test
    void testWebhook_webhookNotFound_throwsResourceNotFoundException() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        when(webhookService.getWebhook(eq(webhookId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Webhook not found: " + webhookId));

        assertThatThrownBy(() -> webhookController.testWebhook(webhookId, user))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void testWebhook_accessDenied_throwsAccessDeniedException() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        when(webhookService.getWebhook(eq(webhookId), any(UUID.class)))
                .thenThrow(new AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> webhookController.testWebhook(webhookId, user))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void testWebhook_passesCorrectPayloadAndSkipAuth() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        var webhook = WebhookResponse.builder()
                .id(webhookId)
                .flowId(UUID.randomUUID())
                .name("test-payload-check")
                .path("check-path")
                .method("GET")
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(webhookService.getWebhook(eq(webhookId), any(UUID.class))).thenReturn(webhook);
        when(webhookService.triggerWebhook(
                eq("check-path"), eq("GET"),
                eq(Map.of("test", true, "triggeredBy", "manual-test")),
                isNull(), eq(true)))
                .thenReturn(executionId);

        var result = webhookController.testWebhook(webhookId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().get("success")).isEqualTo(true);
        verify(webhookService).triggerWebhook(
                eq("check-path"), eq("GET"),
                eq(Map.of("test", true, "triggeredBy", "manual-test")),
                isNull(), eq(true));
    }

    // ===== deleteWebhook (DELETE /api/webhooks/{id}) =====

    @Test
    void deleteWebhook_success_returnsNoContent() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        var webhook = sampleWebhookResponse();
        when(webhookService.getWebhook(eq(webhookId), any(UUID.class))).thenReturn(webhook);

        var result = webhookController.deleteWebhook(webhookId, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(result.getBody()).isNull();
        verify(webhookService).getWebhook(eq(webhookId), any(UUID.class));
        verify(webhookService).deleteWebhook(eq(webhookId), any(UUID.class));
    }

    @Test
    void deleteWebhook_logsActivity() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        var webhook = WebhookResponse.builder()
                .id(webhookId)
                .flowId(UUID.randomUUID())
                .name("delete-log-test")
                .path("delete-log-path")
                .method("POST")
                .isActive(true)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(webhookService.getWebhook(eq(webhookId), any(UUID.class))).thenReturn(webhook);

        webhookController.deleteWebhook(webhookId, user);

        verify(activityService).logActivity(
                any(UUID.class), eq(ActivityService.WEBHOOK_DELETE), eq("webhook"),
                eq(webhookId), eq("delete-log-path"), isNull());
    }

    @Test
    void deleteWebhook_notFound_throwsResourceNotFoundException() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        when(webhookService.getWebhook(eq(webhookId), any(UUID.class)))
                .thenThrow(new ResourceNotFoundException("Webhook not found: " + webhookId));

        assertThatThrownBy(() -> webhookController.deleteWebhook(webhookId, user))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(webhookService, never()).deleteWebhook(any(), any());
    }

    @Test
    void deleteWebhook_accessDenied_throwsAccessDeniedException() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        when(webhookService.getWebhook(eq(webhookId), any(UUID.class)))
                .thenThrow(new AccessDeniedException("Access denied"));

        assertThatThrownBy(() -> webhookController.deleteWebhook(webhookId, user))
                .isInstanceOf(AccessDeniedException.class);
        verify(webhookService, never()).deleteWebhook(any(), any());
    }

    // ===== Edge cases =====

    @Test
    void listWebhooks_parsesUserIdCorrectly() {
        UUID userId = UUID.randomUUID();
        var user = User.withUsername(userId.toString())
                .password("test")
                .authorities("ROLE_USER")
                .build();
        when(webhookService.listWebhooks(eq(userId))).thenReturn(List.of());

        var result = webhookController.listWebhooks(user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(webhookService).listWebhooks(eq(userId));
    }

    @Test
    void createWebhook_withAuthConfig_success() {
        var user = testUser();
        var request = new CreateWebhookRequest();
        request.setFlowId(UUID.randomUUID());
        request.setName("hmac-webhook");
        request.setPath("hmac-path");
        request.setMethod("POST");
        request.setAuthType("hmac");
        request.setAuthConfig(Map.of("secret", "my-secret-key"));

        var webhook = WebhookResponse.builder()
                .id(UUID.randomUUID())
                .flowId(request.getFlowId())
                .name("hmac-webhook")
                .path("hmac-path")
                .method("POST")
                .isActive(false)
                .authType("hmac")
                .authConfig(Map.of("secret", "my-secret-key"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(webhookService.createWebhook(eq(request), any(UUID.class))).thenReturn(webhook);

        var result = webhookController.createWebhook(request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getAuthType()).isEqualTo("hmac");
        assertThat(result.getBody().getAuthConfig()).containsEntry("secret", "my-secret-key");
    }

    @Test
    void updateWebhook_withAuthTypeChange_success() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        var request = new UpdateWebhookRequest();
        request.setAuthType("hmac");
        request.setAuthConfig(Map.of("secret", "new-secret"));

        var updated = WebhookResponse.builder()
                .id(webhookId)
                .flowId(UUID.randomUUID())
                .name("auth-updated")
                .path("auth-path")
                .method("POST")
                .isActive(true)
                .authType("hmac")
                .authConfig(Map.of("secret", "new-secret"))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        when(webhookService.updateWebhook(eq(webhookId), eq(request), any(UUID.class))).thenReturn(updated);

        var result = webhookController.updateWebhook(webhookId, request, user);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getAuthType()).isEqualTo("hmac");
    }

    @Test
    void getWebhook_returnsAllFields() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        UUID flowId = UUID.randomUUID();
        Instant now = Instant.now();

        var webhook = WebhookResponse.builder()
                .id(webhookId)
                .flowId(flowId)
                .name("full-webhook")
                .path("full-path")
                .method("GET")
                .isActive(true)
                .authType("hmac")
                .authConfig(Map.of("secret", "test"))
                .webhookUrl("http://localhost:8080/webhook/full-path")
                .createdAt(now)
                .updatedAt(now)
                .build();
        when(webhookService.getWebhook(eq(webhookId), any(UUID.class))).thenReturn(webhook);

        var result = webhookController.getWebhook(webhookId, user);

        assertThat(result.getBody()).isNotNull();
        var body = result.getBody();
        assertThat(body.getId()).isEqualTo(webhookId);
        assertThat(body.getFlowId()).isEqualTo(flowId);
        assertThat(body.getName()).isEqualTo("full-webhook");
        assertThat(body.getPath()).isEqualTo("full-path");
        assertThat(body.getMethod()).isEqualTo("GET");
        assertThat(body.isActive()).isTrue();
        assertThat(body.getAuthType()).isEqualTo("hmac");
        assertThat(body.getAuthConfig()).containsEntry("secret", "test");
        assertThat(body.getWebhookUrl()).isEqualTo("http://localhost:8080/webhook/full-path");
        assertThat(body.getCreatedAt()).isEqualTo(now);
        assertThat(body.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void deleteWebhook_getsWebhookBeforeDelete_forActivityLog() {
        var user = testUser();
        UUID webhookId = UUID.randomUUID();
        var webhook = sampleWebhookResponse();
        when(webhookService.getWebhook(eq(webhookId), any(UUID.class))).thenReturn(webhook);

        webhookController.deleteWebhook(webhookId, user);

        var inOrder = inOrder(webhookService, activityService);
        inOrder.verify(webhookService).getWebhook(eq(webhookId), any(UUID.class));
        inOrder.verify(webhookService).deleteWebhook(eq(webhookId), any(UUID.class));
        inOrder.verify(activityService).logActivity(
                any(UUID.class), eq(ActivityService.WEBHOOK_DELETE), eq("webhook"),
                eq(webhookId), eq(webhook.getPath()), isNull());
    }
}
