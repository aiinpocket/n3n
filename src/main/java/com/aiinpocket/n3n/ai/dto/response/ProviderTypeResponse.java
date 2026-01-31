package com.aiinpocket.n3n.ai.dto.response;

import com.aiinpocket.n3n.ai.provider.AiProvider;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * AI Provider 類型資訊回應
 */
@Data
@Builder
public class ProviderTypeResponse {

    private String id;
    private String displayName;
    private String defaultBaseUrl;
    private int defaultTimeoutMs;
    private boolean requiresApiKey;
    private Map<String, Object> configSchema;

    public static ProviderTypeResponse from(AiProvider provider) {
        return ProviderTypeResponse.builder()
                .id(provider.getProviderId())
                .displayName(provider.getDisplayName())
                .defaultBaseUrl(provider.getDefaultBaseUrl())
                .defaultTimeoutMs(provider.getDefaultTimeoutMs())
                .requiresApiKey(provider.requiresApiKey())
                .configSchema(provider.getConfigSchema())
                .build();
    }
}
