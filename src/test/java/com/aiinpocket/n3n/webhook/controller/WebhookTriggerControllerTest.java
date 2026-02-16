package com.aiinpocket.n3n.webhook.controller;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.auth.security.IpRateLimiter;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.webhook.service.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookTriggerControllerTest {

    @Mock
    private WebhookService webhookService;

    @Mock
    private ActivityService activityService;

    @Mock
    private IpRateLimiter ipRateLimiter;

    @InjectMocks
    private WebhookTriggerController controller;

    private HttpServletRequest mockRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        lenient().when(request.getHeader(anyString())).thenReturn(null);
        lenient().when(request.getHeader("User-Agent")).thenReturn("TestAgent/1.0");
        return request;
    }

    @Test
    void handlePost_success() {
        UUID executionId = UUID.randomUUID();
        HttpServletRequest request = mockRequest();
        when(webhookService.triggerWebhook(eq("my-hook"), eq("POST"), any(), isNull(), eq(request)))
                .thenReturn(executionId);

        ResponseEntity<Map<String, Object>> response = controller.handlePost(
                "my-hook", Map.of("key", "value"), null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).containsEntry("success", true);
        assertThat(response.getBody()).containsEntry("executionId", executionId.toString());
        verify(activityService).logWebhookTrigger(eq("my-hook"), eq(executionId), anyString(), anyString(), anyInt());
    }

    @Test
    void handleGet_success() {
        UUID executionId = UUID.randomUUID();
        HttpServletRequest request = mockRequest();
        when(webhookService.triggerWebhook(eq("test-path"), eq("GET"), any(), isNull(), eq(request)))
                .thenReturn(executionId);

        ResponseEntity<Map<String, Object>> response = controller.handleGet(
                "test-path", Map.of(), null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        assertThat(response.getBody()).containsEntry("success", true);
    }

    @Test
    void handlePost_invalidPath_returnsBadRequest() {
        HttpServletRequest request = mockRequest();

        ResponseEntity<Map<String, Object>> response = controller.handlePost(
                "../traversal", Map.of(), null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("success", false);
        assertThat(response.getBody()).containsEntry("error", "Invalid webhook path");
        verifyNoInteractions(webhookService);
    }

    @Test
    void handlePost_blankPath_returnsBadRequest() {
        HttpServletRequest request = mockRequest();

        ResponseEntity<Map<String, Object>> response = controller.handlePost(
                " ", Map.of(), null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).containsEntry("success", false);
        verifyNoInteractions(webhookService);
    }

    @Test
    void handlePost_pathWithSpecialChars_returnsBadRequest() {
        HttpServletRequest request = mockRequest();

        ResponseEntity<Map<String, Object>> response = controller.handlePost(
                "path/with/slashes", Map.of(), null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verifyNoInteractions(webhookService);
    }

    @Test
    void handlePost_validPathWithHyphensAndUnderscores_succeeds() {
        UUID executionId = UUID.randomUUID();
        HttpServletRequest request = mockRequest();
        when(webhookService.triggerWebhook(eq("my_hook-123"), eq("POST"), any(), isNull(), eq(request)))
                .thenReturn(executionId);

        ResponseEntity<Map<String, Object>> response = controller.handlePost(
                "my_hook-123", Map.of(), null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
    }

    @Test
    void handlePost_authFailure_returns404() {
        HttpServletRequest request = mockRequest();
        when(webhookService.triggerWebhook(anyString(), anyString(), any(), isNull(), eq(request)))
                .thenThrow(new SecurityException("Auth failed"));

        ResponseEntity<Map<String, Object>> response = controller.handlePost(
                "my-hook", Map.of(), null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).containsEntry("error", "Not found");
        verify(activityService).logWebhookTriggerFailed(eq("my-hook"), anyString(), eq("Auth failed"));
    }

    @Test
    void handlePost_webhookNotFound_returns404() {
        HttpServletRequest request = mockRequest();
        when(webhookService.triggerWebhook(anyString(), anyString(), any(), isNull(), eq(request)))
                .thenThrow(new ResourceNotFoundException("Webhook not found"));

        ResponseEntity<Map<String, Object>> response = controller.handlePost(
                "nonexistent", Map.of(), null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).containsEntry("error", "Not found");
    }

    @Test
    void handlePost_unexpectedError_returns500() {
        HttpServletRequest request = mockRequest();
        when(webhookService.triggerWebhook(anyString(), anyString(), any(), isNull(), eq(request)))
                .thenThrow(new RuntimeException("Unexpected"));

        ResponseEntity<Map<String, Object>> response = controller.handlePost(
                "my-hook", Map.of(), null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).containsEntry("error", "Webhook processing failed");
    }

    @Test
    void handleDelete_success() {
        UUID executionId = UUID.randomUUID();
        HttpServletRequest request = mockRequest();
        when(webhookService.triggerWebhook(eq("my-hook"), eq("DELETE"), any(), isNull(), eq(request)))
                .thenReturn(executionId);

        ResponseEntity<Map<String, Object>> response = controller.handleDelete(
                "my-hook", null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
    }

    @Test
    void handlePut_success() {
        UUID executionId = UUID.randomUUID();
        HttpServletRequest request = mockRequest();
        when(webhookService.triggerWebhook(eq("my-hook"), eq("PUT"), any(), isNull(), eq(request)))
                .thenReturn(executionId);

        ResponseEntity<Map<String, Object>> response = controller.handlePut(
                "my-hook", Map.of("data", "test"), null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
    }

    @Test
    void handlePost_withSignature_passesToService() {
        UUID executionId = UUID.randomUUID();
        HttpServletRequest request = mockRequest();
        when(webhookService.triggerWebhook(eq("my-hook"), eq("POST"), any(), eq("sha256=abc123"), eq(request)))
                .thenReturn(executionId);

        ResponseEntity<Map<String, Object>> response = controller.handlePost(
                "my-hook", Map.of(), "sha256=abc123", request);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        verify(webhookService).triggerWebhook(eq("my-hook"), eq("POST"), any(), eq("sha256=abc123"), eq(request));
    }

    @Test
    void handlePost_nullBody_usesEmptyMap() {
        UUID executionId = UUID.randomUUID();
        HttpServletRequest request = mockRequest();
        when(webhookService.triggerWebhook(eq("my-hook"), eq("POST"), eq(Map.of()), isNull(), eq(request)))
                .thenReturn(executionId);

        ResponseEntity<Map<String, Object>> response = controller.handlePost(
                "my-hook", null, null, request);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
    }

    @Test
    void handleGet_extractsIpFromXForwardedFor() {
        UUID executionId = UUID.randomUUID();
        HttpServletRequest request = mockRequest();
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.50, 10.0.0.1");
        when(webhookService.triggerWebhook(anyString(), anyString(), any(), isNull(), eq(request)))
                .thenReturn(executionId);

        controller.handleGet("my-hook", Map.of(), null, request);

        verify(activityService).logWebhookTrigger(eq("my-hook"), eq(executionId), eq("203.0.113.50"), anyString(), anyInt());
    }

    @Test
    void handleGet_extractsIpFromXRealIp() {
        UUID executionId = UUID.randomUUID();
        HttpServletRequest request = mockRequest();
        when(request.getHeader("X-Real-IP")).thenReturn("198.51.100.10");
        when(webhookService.triggerWebhook(anyString(), anyString(), any(), isNull(), eq(request)))
                .thenReturn(executionId);

        controller.handleGet("my-hook", Map.of(), null, request);

        verify(activityService).logWebhookTrigger(eq("my-hook"), eq(executionId), eq("198.51.100.10"), anyString(), anyInt());
    }

    @Test
    void handleGet_invalidSpoofedIp_fallsBackToRemoteAddr() {
        UUID executionId = UUID.randomUUID();
        HttpServletRequest request = mockRequest();
        when(request.getHeader("X-Forwarded-For")).thenReturn("not-a-valid-ip");
        when(webhookService.triggerWebhook(anyString(), anyString(), any(), isNull(), eq(request)))
                .thenReturn(executionId);

        controller.handleGet("my-hook", Map.of(), null, request);

        verify(activityService).logWebhookTrigger(eq("my-hook"), eq(executionId), eq("127.0.0.1"), anyString(), anyInt());
    }
}
