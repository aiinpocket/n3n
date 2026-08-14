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
import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.repository.UserRepository;
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
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final WebhookRepository webhookRepository;
    private final UserRepository userRepository;
    private final ExecutionService executionService;
    private final FlowVersionRepository flowVersionRepository;
    private final FlowShareService flowShareService;
    private final ObjectMapper objectMapper;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    private static final String NS_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int NS_LENGTH = 8;
    private static final SecureRandom NS_RANDOM = new SecureRandom();

    /**
     * 取得使用者的 webhook 命名空間；尚未有時惰性產生一組隨機短碼
     * （非帳號衍生，避免公開網址暴露帳號）並存回 users.webhook_ns。
     */
    @Transactional
    public String resolveWebhookNs(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        if (user.getWebhookNs() != null && !user.getWebhookNs().isBlank()) {
            return user.getWebhookNs();
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = randomNs();
            if (!userRepository.existsByWebhookNs(candidate)) {
                user.setWebhookNs(candidate);
                userRepository.save(user);
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to allocate webhook namespace");
    }

    private static String randomNs() {
        StringBuilder sb = new StringBuilder(NS_LENGTH);
        for (int i = 0; i < NS_LENGTH; i++) {
            sb.append(NS_ALPHABET.charAt(NS_RANDOM.nextInt(NS_ALPHABET.length())));
        }
        return sb.toString();
    }

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
        String ns = resolveWebhookNs(userId);
        // 唯一性以使用者命名空間為界：不同使用者可各自使用相同的 path
        if (webhookRepository.existsByNsAndPathAndMethod(ns, request.getPath(), method)) {
            throw new IllegalArgumentException("Webhook path already exists for method " + method + ": " + request.getPath());
        }

        Webhook webhook = Webhook.builder()
            .flowId(request.getFlowId())
            .name(request.getName())
            .ns(ns)
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
    public UUID triggerWebhook(String path, String method, Map<String, Object> payload, String signature) {
        return triggerWebhook(path, method, payload, signature, (HttpServletRequest) null, false);
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
        // 舊式網址（無命名空間）只對應遷移前建立的 webhook
        Webhook webhook = webhookRepository.findByNsIsNullAndPathAndMethodAndIsActiveTrue(path, method.toUpperCase())
            .orElseThrow(() -> new ResourceNotFoundException("Webhook not found or inactive: " + path));
        return trigger(webhook, method, payload, signature, request, skipAuth);
    }

    @Transactional
    public UUID triggerWebhook(String ns, String path, String method, Map<String, Object> payload, String signature, HttpServletRequest request, boolean skipAuth) {
        Webhook webhook = webhookRepository.findByNsAndPathAndMethodAndIsActiveTrue(ns, path, method.toUpperCase())
            .orElseThrow(() -> new ResourceNotFoundException("Webhook not found or inactive: " + ns + "/" + path));
        return trigger(webhook, method, payload, signature, request, skipAuth);
    }

    private UUID trigger(Webhook webhook, String method, Map<String, Object> payload, String signature, HttpServletRequest request, boolean skipAuth) {

        // Validate auth (skip only for internal test triggers authenticated via JWT).
        // A webhook with no configured auth type is treated as requiring explicit configuration:
        // it is rejected rather than allowed through. Only an explicit "none" opts out of auth.
        if (!skipAuth) {
            String authType = webhook.getAuthType();
            if (authType == null || authType.isBlank()) {
                throw new SecurityException("Webhook authentication is not configured");
            }
            switch (authType) {
                case "hmac", "signature" -> validateHmacSignature(payload, signature, webhook.getAuthConfig());
                case "apiKey" -> validateApiKey(request, webhook.getAuthConfig());
                case "none" -> { /* Explicitly no auth */ }
                default -> throw new SecurityException("Unsupported webhook auth type: " + authType);
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
