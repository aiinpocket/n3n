package com.aiinpocket.n3n.webhook.controller;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.webhook.service.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhook Triggers", description = "Webhook trigger endpoints")
public class WebhookTriggerController {

    private final WebhookService webhookService;
    private final ActivityService activityService;
    private final com.aiinpocket.n3n.auth.security.IpRateLimiter ipRateLimiter;

    @GetMapping("/{path}")
    public ResponseEntity<Map<String, Object>> handleGet(
            @PathVariable String path,
            @RequestParam(required = false) Map<String, String> params,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            HttpServletRequest request) {
        return handleWebhook(path, "GET", Map.of("params", params), signature, request);
    }

    @PostMapping("/{path}")
    public ResponseEntity<Map<String, Object>> handlePost(
            @PathVariable String path,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            HttpServletRequest request) {
        return handleWebhook(path, "POST", body != null ? body : Map.of(), signature, request);
    }

    @PutMapping("/{path}")
    public ResponseEntity<Map<String, Object>> handlePut(
            @PathVariable String path,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            HttpServletRequest request) {
        return handleWebhook(path, "PUT", body != null ? body : Map.of(), signature, request);
    }

    @DeleteMapping("/{path}")
    public ResponseEntity<Map<String, Object>> handleDelete(
            @PathVariable String path,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature,
            HttpServletRequest request) {
        return handleWebhook(path, "DELETE", Map.of(), signature, request);
    }

    private static final int MAX_PAYLOAD_SIZE = 1_048_576; // 1MB
    private static final java.util.regex.Pattern PATH_PATTERN = java.util.regex.Pattern.compile("^[a-zA-Z0-9_\\-]+$");

    private ResponseEntity<Map<String, Object>> handleWebhook(
            String path, String method, Map<String, Object> payload, String signature, HttpServletRequest request) {
        // Validate webhook path format
        if (path == null || path.isBlank() || !PATH_PATTERN.matcher(path).matches()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Invalid webhook path"
            ));
        }

        String sourceIp = getClientIp(request);

        // Rate limit: 60 webhook triggers per minute per IP
        ipRateLimiter.checkAllowed("webhook-trigger", sourceIp, 60, 60);

        String userAgent = request.getHeader("User-Agent");
        int payloadSize = payload != null ? payload.toString().length() : 0;

        // Validate payload size
        if (payloadSize > MAX_PAYLOAD_SIZE) {
            log.warn("Webhook payload too large for path: {}, size={}, sourceIp={}", path, payloadSize, sourceIp);
            return ResponseEntity.status(413).body(Map.of(
                "success", false,
                "error", "Payload too large"
            ));
        }

        try {
            UUID executionId = webhookService.triggerWebhook(path, method, payload, signature);
            log.info("Webhook {} triggered successfully, executionId={}, sourceIp={}", path, executionId, sourceIp);

            // Log webhook trigger for security analysis
            activityService.logWebhookTrigger(path, executionId, sourceIp, userAgent, payloadSize);

            return ResponseEntity.accepted().body(Map.of(
                "success", true,
                "executionId", executionId.toString(),
                "message", "Flow execution started"
            ));
        } catch (SecurityException e) {
            log.warn("Webhook signature validation failed for path: {}, sourceIp={}", path, sourceIp);
            activityService.logWebhookTriggerFailed(path, sourceIp, "Invalid signature");
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "error", "Invalid signature"
            ));
        } catch (com.aiinpocket.n3n.common.exception.ResourceNotFoundException e) {
            log.warn("Webhook not found: path={}, sourceIp={}", path, sourceIp);
            activityService.logWebhookTriggerFailed(path, sourceIp, "Not found");
            return ResponseEntity.status(404).body(Map.of(
                "success", false,
                "error", "Webhook not found"
            ));
        } catch (Exception e) {
            log.error("Webhook trigger failed for path: {}, sourceIp={}", path, sourceIp, e);
            activityService.logWebhookTriggerFailed(path, sourceIp, "Webhook trigger failed");
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "Webhook processing failed"
            ));
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = null;
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            ip = xForwardedFor.split(",")[0].trim();
        }
        if (ip == null) {
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                ip = xRealIp;
            }
        }
        if (ip != null) {
            // Validate IP format to prevent spoofed values
            try {
                java.net.InetAddress.getByName(ip);
                return ip;
            } catch (java.net.UnknownHostException e) {
                // Invalid IP, fall through
            }
        }
        return request.getRemoteAddr();
    }
}
