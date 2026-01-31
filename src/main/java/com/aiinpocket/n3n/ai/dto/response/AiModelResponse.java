package com.aiinpocket.n3n.ai.dto.response;

import com.aiinpocket.n3n.ai.provider.AiModel;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * AI 模型資訊回應
 */
@Data
@Builder
public class AiModelResponse {

    private String id;
    private String displayName;
    private String providerId;
    private int contextWindow;
    private int maxOutputTokens;
    private boolean supportsVision;
    private boolean supportsStreaming;
    private Map<String, Object> capabilities;

    public static AiModelResponse from(AiModel model) {
        return AiModelResponse.builder()
                .id(model.getId())
                .displayName(model.getDisplayName())
                .providerId(model.getProviderId())
                .contextWindow(model.getContextWindow())
                .maxOutputTokens(model.getMaxOutputTokens())
                .supportsVision(model.isSupportsVision())
                .supportsStreaming(model.isSupportsStreaming())
                .capabilities(model.getCapabilities())
                .build();
    }
}
