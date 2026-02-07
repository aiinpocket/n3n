package com.aiinpocket.n3n.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

/**
 * 建立 AI Provider 設定請求
 */
@Data
public class CreateAiProviderRequest {

    @NotBlank(message = "Provider type cannot be blank")
    @Pattern(regexp = "^(claude|openai|gemini|ollama)$", message = "Unsupported provider type")
    private String provider;

    @NotBlank(message = "Name cannot be blank")
    private String name;

    private String description;

    /**
     * API Key（建立時傳入，會加密儲存）
     */
    private String apiKey;

    /**
     * 自訂 Base URL（用於 Ollama 或代理）
     */
    private String baseUrl;

    /**
     * 預設模型
     */
    private String defaultModel;

    /**
     * 供應商特定設定
     */
    private Map<String, Object> settings;

    /**
     * 是否設為預設
     */
    private Boolean isDefault;

    /**
     * Workspace ID
     */
    private UUID workspaceId;
}
