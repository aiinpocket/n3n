package com.aiinpocket.n3n.webhook.dto;

import com.aiinpocket.n3n.webhook.entity.Webhook;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashMap;
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
            .authConfig(maskSecrets(webhook.getAuthConfig()))
            .webhookUrl(baseUrl + "/webhook/" + webhook.getPath())
            .createdAt(webhook.getCreatedAt())
            .updatedAt(webhook.getUpdatedAt())
            .build();
    }

    /**
     * Mask sensitive values in authConfig to prevent secret leakage in API responses.
     */
    private static Map<String, Object> maskSecrets(Map<String, Object> authConfig) {
        if (authConfig == null || authConfig.isEmpty()) {
            return authConfig;
        }
        Map<String, Object> masked = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : authConfig.entrySet()) {
            String key = entry.getKey().toLowerCase();
            if (key.contains("secret") || key.contains("password") || key.contains("token") || key.contains("key")) {
                Object value = entry.getValue();
                if (value instanceof String str && str.length() > 4) {
                    masked.put(entry.getKey(), str.substring(0, 4) + "****");
                } else {
                    masked.put(entry.getKey(), "****");
                }
            } else {
                masked.put(entry.getKey(), entry.getValue());
            }
        }
        return masked;
    }
}
