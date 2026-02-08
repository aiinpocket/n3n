package com.aiinpocket.n3n.webhook.service;

import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.dto.CreateExecutionRequest;
import com.aiinpocket.n3n.execution.service.ExecutionService;
import com.aiinpocket.n3n.flow.repository.FlowVersionRepository;
import com.aiinpocket.n3n.webhook.dto.CreateWebhookRequest;
import com.aiinpocket.n3n.webhook.dto.WebhookResponse;
import com.aiinpocket.n3n.webhook.entity.Webhook;
import com.aiinpocket.n3n.webhook.repository.WebhookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public List<WebhookResponse> listWebhooks(UUID userId) {
        return webhookRepository.findByCreatedByOrderByCreatedAtDesc(userId)
            .stream()
            .map(w -> WebhookResponse.from(w, baseUrl))
            .toList();
    }

    public List<WebhookResponse> listWebhooksForFlow(UUID flowId, UUID userId) {
        return webhookRepository.findByFlowIdOrderByCreatedAtDesc(flowId)
            .stream()
            .filter(w -> w.getCreatedBy().equals(userId))
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

    public WebhookResponse getWebhook(UUID id, UUID userId) {
        Webhook webhook = findWebhookWithOwnerCheck(id, userId);
        return WebhookResponse.from(webhook, baseUrl);
    }

    @Transactional
    public WebhookResponse createWebhook(CreateWebhookRequest request, UUID userId) {
        String method = request.getMethod() != null ? request.getMethod() : "POST";
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
    public UUID triggerWebhook(String path, String method, Map<String, Object> payload, String signature) {
        Webhook webhook = webhookRepository.findByPathAndMethodAndIsActiveTrue(path, method.toUpperCase())
            .orElseThrow(() -> new ResourceNotFoundException("Webhook not found or inactive: " + path));

        // Validate signature if auth is configured
        if (webhook.getAuthType() != null && "hmac".equals(webhook.getAuthType())) {
            validateHmacSignature(payload, signature, webhook.getAuthConfig());
        }

        // Get the published version of the flow to execute
        var publishedVersion = flowVersionRepository.findByFlowIdAndStatus(webhook.getFlowId(), "published")
            .orElseThrow(() -> new IllegalStateException("No published version available for flow"));

        // Start execution with webhook payload as input
        CreateExecutionRequest request = new CreateExecutionRequest();
        request.setFlowId(webhook.getFlowId());
        request.setVersion(publishedVersion.getVersion());
        request.setInput(payload);

        var execution = executionService.createExecution(request, webhook.getCreatedBy());
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
}
