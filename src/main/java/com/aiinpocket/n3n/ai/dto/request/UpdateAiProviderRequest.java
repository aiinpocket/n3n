package com.aiinpocket.n3n.ai.dto.request;

import lombok.Data;

import java.util.Map;

/**
 * 更新 AI Provider 設定請求
 */
@Data
public class UpdateAiProviderRequest {

    private String name;

    private String description;

    /**
     * 新的 API Key（如果提供，會更新加密儲存）
     */
    private String apiKey;

    private String baseUrl;

    private String defaultModel;

    private Map<String, Object> settings;

    private Boolean isActive;
}
