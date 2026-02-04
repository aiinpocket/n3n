package com.aiinpocket.n3n.webhook.controller;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.webhook.service.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookTriggerController {

    private final WebhookService webhookService;
    private final ActivityService activityService;

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

    private ResponseEntity<Map<String, Object>> handleWebhook(
            String path, String method, Map<String, Object> payload, String signature, HttpServletRequest request) {
        String sourceIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        int payloadSize = payload != null ? payload.toString().length() : 0;

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
        } catch (Exception e) {
            log.error("Webhook trigger failed for path: {}, sourceIp={}", path, sourceIp, e);
            activityService.logWebhookTriggerFailed(path, sourceIp, e.getMessage());
            return ResponseEntity.status(404).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
