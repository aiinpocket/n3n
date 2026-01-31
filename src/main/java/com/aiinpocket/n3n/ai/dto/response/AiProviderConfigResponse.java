package com.aiinpocket.n3n.ai.dto.response;

import com.aiinpocket.n3n.ai.entity.AiProviderConfig;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * AI Provider 設定回應
 */
@Data
@Builder
public class AiProviderConfigResponse {

    private UUID id;
    private String provider;
    private String name;
    private String description;
    private String baseUrl;
    private String defaultModel;
    private Map<String, Object> settings;
    private Boolean isActive;
    private Boolean isDefault;
    private Boolean hasApiKey;
    private Instant createdAt;
    private Instant updatedAt;

    public static AiProviderConfigResponse from(AiProviderConfig config) {
        return AiProviderConfigResponse.builder()
                .id(config.getId())
                .provider(config.getProvider())
                .name(config.getName())
                .description(config.getDescription())
                .baseUrl(config.getBaseUrl())
                .defaultModel(config.getDefaultModel())
                .settings(config.getSettings())
                .isActive(config.getIsActive())
                .isDefault(config.getIsDefault())
                .hasApiKey(config.getCredentialId() != null)
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }
}
