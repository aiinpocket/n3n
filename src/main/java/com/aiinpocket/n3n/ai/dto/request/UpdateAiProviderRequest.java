package com.aiinpocket.n3n.ai.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 更新 AI Provider 設定請求
 */
@Data
public class UpdateAiProviderRequest {

    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    /**
     * 新的 API Key（如果提供，會更新加密儲存）
     */
    @Size(max = 500, message = "API key must be at most 500 characters")
    private String apiKey;

    @Size(max = 2000, message = "Base URL must be at most 2000 characters")
    private String baseUrl;

    @Size(max = 100, message = "Default model must be at most 100 characters")
    private String defaultModel;

    @Size(max = 30, message = "Settings must have at most 30 fields")
    private Map<String, Object> settings;

    private Boolean isActive;
}
