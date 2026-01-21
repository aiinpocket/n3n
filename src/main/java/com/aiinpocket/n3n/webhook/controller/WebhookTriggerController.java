package com.aiinpocket.n3n.webhook.controller;

import com.aiinpocket.n3n.webhook.service.WebhookService;
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

    @GetMapping("/{path}")
    public ResponseEntity<Map<String, Object>> handleGet(
            @PathVariable String path,
            @RequestParam(required = false) Map<String, String> params,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {
        return handleWebhook(path, "GET", Map.of("params", params), signature);
    }

    @PostMapping("/{path}")
    public ResponseEntity<Map<String, Object>> handlePost(
            @PathVariable String path,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {
        return handleWebhook(path, "POST", body != null ? body : Map.of(), signature);
    }

    @PutMapping("/{path}")
    public ResponseEntity<Map<String, Object>> handlePut(
            @PathVariable String path,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {
        return handleWebhook(path, "PUT", body != null ? body : Map.of(), signature);
    }

    @DeleteMapping("/{path}")
    public ResponseEntity<Map<String, Object>> handleDelete(
            @PathVariable String path,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {
        return handleWebhook(path, "DELETE", Map.of(), signature);
    }

    private ResponseEntity<Map<String, Object>> handleWebhook(
            String path, String method, Map<String, Object> payload, String signature) {
        try {
            UUID executionId = webhookService.triggerWebhook(path, method, payload, signature);
            log.info("Webhook {} triggered successfully, executionId={}", path, executionId);
            return ResponseEntity.accepted().body(Map.of(
                "success", true,
                "executionId", executionId.toString(),
                "message", "Flow execution started"
            ));
        } catch (SecurityException e) {
            log.warn("Webhook signature validation failed for path: {}", path);
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "error", "Invalid signature"
            ));
        } catch (Exception e) {
            log.error("Webhook trigger failed for path: {}", path, e);
            return ResponseEntity.status(404).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
