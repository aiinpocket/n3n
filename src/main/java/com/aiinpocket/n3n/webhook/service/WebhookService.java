package com.aiinpocket.n3n.webhook.service;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.dto.CreateExecutionRequest;
import com.aiinpocket.n3n.execution.service.ExecutionService;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.aiinpocket.n3n.flow.service.FlowShareService;
import com.aiinpocket.n3n.webhook.dto.CreateWebhookRequest;
import com.aiinpocket.n3n.webhook.dto.UpdateWebhookRequest;
import com.aiinpocket.n3n.webhook.dto.WebhookResponse;
import com.aiinpocket.n3n.webhook.entity.Webhook;
import com.aiinpocket.n3n.webhook.repository.WebhookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final WebhookRepository webhookRepository;
    private final ExecutionService executionService;
    private final FlowVersionRepository flowVersionRepository;
    private final FlowShareService flowShareService;
    private final ObjectMapper objectMapper;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    @Transactional(readOnly = true)
    public List<WebhookResponse> listWebhooks(UUID userId) {
        return webhookRepository.findByCreatedByOrderByCreatedAtDesc(userId)
            .stream()
            .map(w -> WebhookResponse.from(w, baseUrl))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<WebhookResponse> listWebhooksForFlow(UUID flowId, UUID userId) {
        // Validate user has access to the flow
        if (!flowShareService.hasAccess(flowId, userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied to flow: " + flowId);
        }
        return webhookRepository.findByFlowIdAndCreatedByOrderByCreatedAtDesc(flowId, userId)
            .stream()
            .map(w -> WebhookResponse.from(w, baseUrl))
            .toList();
    }

    private Webhook findWebhookWithOwnerCheck(UUID id, UUID userId) {
        Webhook webhook = webhookRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Webhook not found: " + id));
        if (!webhook.getCreatedBy().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        }
        return webhook;
    }

    @Transactional(readOnly = true)
    public WebhookResponse getWebhook(UUID id, UUID userId) {
        Webhook webhook = findWebhookWithOwnerCheck(id, userId);
        return WebhookResponse.from(webhook, baseUrl);
    }

    @Transactional
    public WebhookResponse createWebhook(CreateWebhookRequest request, UUID userId) {
        // Validate user has EDIT access to the flow (prevents IDOR)
        String permission = flowShareService.getUserPermission(request.getFlowId(), userId);
        if (permission == null || "view".equals(permission)) {
            throw new org.springframework.security.access.AccessDeniedException("Access denied to flow: " + request.getFlowId());
        }

        String method = request.getMethod() != null ? request.getMethod().toUpperCase() : "POST";
        if (webhookRepository.existsByPathAndMethod(request.getPath(), method)) {
            throw new IllegalArgumentException("Webhook path already exists for method " + method + ": " + request.getPath());
        }

        Webhook webhook = Webhook.builder()
            .flowId(request.getFlowId())
            .name(request.getName())
            .path(request.getPath())
            .method(method)
            .authType(request.getAuthType())
            .authConfig(request.getAuthConfig())
            .createdBy(userId)
            .build();

        webhook = webhookRepository.save(webhook);
        log.info("Webhook created: id={}, path={}, flowId={}", webhook.getId(), webhook.getPath(), webhook.getFlowId());

        return WebhookResponse.from(webhook, baseUrl);
    }

    @Transactional
    public WebhookResponse updateWebhook(UUID id, UpdateWebhookRequest request, UUID userId) {
        Webhook webhook = findWebhookWithOwnerCheck(id, userId);

        if (request.getName() != null) {
            webhook.setName(request.getName());
        }
        if (request.getAuthType() != null) {
            webhook.setAuthType(request.getAuthType());
            webhook.setAuthConfig(request.getAuthConfig());
        }

        webhook = webhookRepository.save(webhook);
        log.info("Webhook updated: id={}, path={}", id, webhook.getPath());
        return WebhookResponse.from(webhook, baseUrl);
    }

    @Transactional
    public WebhookResponse activateWebhook(UUID id, UUID userId) {
        Webhook webhook = findWebhookWithOwnerCheck(id, userId);
        webhook.setIsActive(true);
        webhook = webhookRepository.save(webhook);
        return WebhookResponse.from(webhook, baseUrl);
    }

    @Transactional
    public WebhookResponse deactivateWebhook(UUID id, UUID userId) {
        Webhook webhook = findWebhookWithOwnerCheck(id, userId);
        webhook.setIsActive(false);
        webhook = webhookRepository.save(webhook);
        return WebhookResponse.from(webhook, baseUrl);
    }

    @Transactional
    public void deleteWebhook(UUID id, UUID userId) {
        findWebhookWithOwnerCheck(id, userId);
        webhookRepository.deleteById(id);
        log.info("Webhook deleted: id={}", id);
    }

    @Transactional
    public UUID triggerWebhook(String path, String method, Map<String, Object> payload, String signature, HttpServletRequest request) {
        return triggerWebhook(path, method, payload, signature, request, false);
    }

    /**
     * Trigger webhook with optional auth skip for internal test triggers.
     * When skipAuth=true, auth validation is skipped (caller must be authenticated via JWT).
     */
    @Transactional
    public UUID triggerWebhook(String path, String method, Map<String, Object> payload, String signature, boolean skipAuth) {
        return triggerWebhook(path, method, payload, signature, null, skipAuth);
    }

    @Transactional
    public UUID triggerWebhook(String path, String method, Map<String, Object> payload, String signature, HttpServletRequest request, boolean skipAuth) {
        Webhook webhook = webhookRepository.findByPathAndMethodAndIsActiveTrue(path, method.toUpperCase())
            .orElseThrow(() -> new ResourceNotFoundException("Webhook not found or inactive: " + path));

        // Validate auth if configured (skip for internal test triggers)
        if (!skipAuth && webhook.getAuthType() != null) {
            switch (webhook.getAuthType()) {
                case "hmac", "signature" -> validateHmacSignature(payload, signature, webhook.getAuthConfig());
                case "apiKey" -> validateApiKey(request, webhook.getAuthConfig());
                default -> { /* no-auth or unknown type: allow */ }
            }
        }

        // Get the published version of the flow to execute
        var publishedVersion = flowVersionRepository.findByFlowIdAndStatus(webhook.getFlowId(), "published")
            .orElseThrow(() -> new IllegalStateException("No published version available for flow"));

        // Start execution with webhook payload as input
        CreateExecutionRequest execRequest = new CreateExecutionRequest();
        execRequest.setFlowId(webhook.getFlowId());
        execRequest.setVersion(publishedVersion.getVersion());
        execRequest.setInput(payload);

        var execution = executionService.createExecution(execRequest, webhook.getCreatedBy());
        log.info("Webhook triggered execution: webhookId={}, executionId={}", webhook.getId(), execution.getId());

        return execution.getId();
    }

    @SuppressWarnings("unchecked")
    private void validateHmacSignature(Map<String, Object> payload, String signature, Map<String, Object> authConfig) {
        if (signature == null || authConfig == null) {
            throw new SecurityException("Missing signature for HMAC authentication");
        }

        String secret = (String) authConfig.get("secret");
        if (secret == null) {
            throw new SecurityException("HMAC secret not configured");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            // Use deterministic JSON serialization instead of Map.toString()
            String payloadJson = objectMapper.writeValueAsString(payload);
            byte[] hash = mac.doFinal(payloadJson.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getEncoder().encodeToString(hash);

            // Constant-time comparison to prevent timing attacks
            if (!MessageDigest.isEqual(
                    expectedSignature.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8))) {
                throw new SecurityException("Invalid webhook signature");
            }
        } catch (SecurityException e) {
            throw e;
        } catch (java.security.NoSuchAlgorithmException e) {
            log.error("HMAC algorithm not available: {}", e.getMessage());
            throw new SecurityException("Internal cryptographic error");
        } catch (java.security.InvalidKeyException e) {
            log.error("Invalid HMAC key for webhook: {}", e.getMessage());
            throw new SecurityException("Invalid webhook secret key configuration");
        } catch (Exception e) {
            log.error("Unexpected error during HMAC validation: {}", e.getMessage(), e);
            throw new SecurityException("Signature validation failed");
        }
    }

    private void validateApiKey(HttpServletRequest request, Map<String, Object> authConfig) {
        if (request == null || authConfig == null) {
            throw new SecurityException("Missing API key for authentication");
        }

        String headerName = (String) authConfig.get("headerName");
        String expectedApiKey = (String) authConfig.get("apiKey");

        if (headerName == null || expectedApiKey == null) {
            throw new SecurityException("API key authentication not properly configured");
        }

        String providedApiKey = request.getHeader(headerName);
        if (providedApiKey == null) {
            throw new SecurityException("Missing API key header: " + headerName);
        }

        // Constant-time comparison to prevent timing attacks
        if (!MessageDigest.isEqual(
                expectedApiKey.getBytes(StandardCharsets.UTF_8),
                providedApiKey.getBytes(StandardCharsets.UTF_8))) {
            throw new SecurityException("Invalid API key");
        }
    }
}
