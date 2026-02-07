package com.aiinpocket.n3n.webhook.controller;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.webhook.dto.CreateWebhookRequest;
import com.aiinpocket.n3n.webhook.dto.WebhookResponse;
import com.aiinpocket.n3n.webhook.service.WebhookService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Tag(name = "Webhooks", description = "Webhook management")
public class WebhookController {

    private final WebhookService webhookService;
    private final ActivityService activityService;

    @GetMapping
    public ResponseEntity<List<WebhookResponse>> listWebhooks(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(webhookService.listWebhooks(userId));
    }

    @GetMapping("/flow/{flowId}")
    public ResponseEntity<List<WebhookResponse>> listWebhooksForFlow(
            @PathVariable UUID flowId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(webhookService.listWebhooksForFlow(flowId, userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<WebhookResponse> getWebhook(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(webhookService.getWebhook(id, userId));
    }

    @PostMapping
    public ResponseEntity<WebhookResponse> createWebhook(
            @Valid @RequestBody CreateWebhookRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        WebhookResponse response = webhookService.createWebhook(request, userId);
        activityService.logWebhookCreate(userId, response.getId(), response.getPath(), response.getFlowId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<WebhookResponse> activateWebhook(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(webhookService.activateWebhook(id, userId));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<WebhookResponse> deactivateWebhook(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(webhookService.deactivateWebhook(id, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWebhook(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        WebhookResponse webhook = webhookService.getWebhook(id, userId);
        webhookService.deleteWebhook(id, userId);
        activityService.logActivity(userId, ActivityService.WEBHOOK_DELETE, "webhook", id, webhook.getPath(), null);
        return ResponseEntity.noContent().build();
    }
}
