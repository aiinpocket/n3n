package com.aiinpocket.n3n.webhook.controller;

import com.aiinpocket.n3n.webhook.dto.CreateWebhookRequest;
import com.aiinpocket.n3n.webhook.dto.WebhookResponse;
import com.aiinpocket.n3n.webhook.service.WebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @GetMapping
    public ResponseEntity<List<WebhookResponse>> listWebhooks(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(webhookService.listWebhooks(userId));
    }

    @GetMapping("/flow/{flowId}")
    public ResponseEntity<List<WebhookResponse>> listWebhooksForFlow(@PathVariable UUID flowId) {
        return ResponseEntity.ok(webhookService.listWebhooksForFlow(flowId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WebhookResponse> getWebhook(@PathVariable UUID id) {
        return ResponseEntity.ok(webhookService.getWebhook(id));
    }

    @PostMapping
    public ResponseEntity<WebhookResponse> createWebhook(
            @Valid @RequestBody CreateWebhookRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(webhookService.createWebhook(request, userId));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<WebhookResponse> activateWebhook(@PathVariable UUID id) {
        return ResponseEntity.ok(webhookService.activateWebhook(id));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<WebhookResponse> deactivateWebhook(@PathVariable UUID id) {
        return ResponseEntity.ok(webhookService.deactivateWebhook(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWebhook(@PathVariable UUID id) {
        webhookService.deleteWebhook(id);
        return ResponseEntity.noContent().build();
    }
}
