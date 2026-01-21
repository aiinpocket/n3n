package com.aiinpocket.n3n.webhook.dto;

import com.aiinpocket.n3n.webhook.entity.Webhook;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class WebhookResponse {

    private UUID id;
    private UUID flowId;
    private String name;
    private String path;
    private String method;
    private boolean isActive;
    private String authType;
    private Map<String, Object> authConfig;
    private String webhookUrl;
    private Instant createdAt;
    private Instant updatedAt;

    public static WebhookResponse from(Webhook webhook, String baseUrl) {
        return WebhookResponse.builder()
            .id(webhook.getId())
            .flowId(webhook.getFlowId())
            .name(webhook.getName())
            .path(webhook.getPath())
            .method(webhook.getMethod())
            .isActive(Boolean.TRUE.equals(webhook.getIsActive()))
            .authType(webhook.getAuthType())
            .authConfig(webhook.getAuthConfig())
            .webhookUrl(baseUrl + "/webhook/" + webhook.getPath())
            .createdAt(webhook.getCreatedAt())
            .updatedAt(webhook.getUpdatedAt())
            .build();
    }
}
