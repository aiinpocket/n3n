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
    private String ns;
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
            .ns(webhook.getNs())
            .path(webhook.getPath())
            .method(webhook.getMethod())
            .isActive(Boolean.TRUE.equals(webhook.getIsActive()))
            .authType(webhook.getAuthType())
            .authConfig(maskSecrets(webhook.getAuthConfig()))
            // 新式網址帶使用者命名空間；舊資料（無 ns）維持原網址
            .webhookUrl(webhook.getNs() != null
                ? baseUrl + "/webhook/" + webhook.getNs() + "/" + webhook.getPath()
                : baseUrl + "/webhook/" + webhook.getPath())
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
            if (key.contains("secret") || key.contains("password") || key.contains("token")
                    || key.contains("key") || key.contains("credential") || key.contains("auth")) {
                masked.put(entry.getKey(), "********");
            } else {
                masked.put(entry.getKey(), entry.getValue());
            }
        }
        return masked;
    }
}
