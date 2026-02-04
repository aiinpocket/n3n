package com.aiinpocket.n3n.execution.handler.handlers.ai.base;

import com.aiinpocket.n3n.ai.provider.*;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.multiop.MultiOperationNodeHandler;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.UUID;

/**
 * AI 節點抽象基類
 * 提供 AI Provider 選擇、串流支援、Token 計量等共用功能
 *
 * 所有 AI 相關節點（Chat, Agent, Chain, Memory 等）應繼承此類
 */
@Slf4j
public abstract class AbstractAiNodeHandler extends MultiOperationNodeHandler
    implements StreamingNodeHandler {

    protected final AiProviderFactory providerFactory;

    protected AbstractAiNodeHandler(AiProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    @Override
    public String getCategory() {
        return "AI";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    /**
     * 根據配置取得 AI Provider
     */
    protected AiProvider resolveProvider(NodeExecutionContext context) {
        String providerId = getStringConfig(context, "provider", "openai");
        return providerFactory.getProvider(providerId);
    }

    /**
     * 根據提供者 ID 取得 AI Provider
     */
    protected AiProvider resolveProvider(String providerId) {
        return providerFactory.getProvider(providerId);
    }

    /**
     * 從憑證或環境變數取得 API Key
     *
     * @param credential 憑證資料
     * @param envVarName 環境變數名稱（如 OPENAI_API_KEY）
     * @return API Key
     */
    protected String resolveApiKey(Map<String, Object> credential, String envVarName) {
        String apiKey = getCredentialValue(credential, "apiKey");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = System.getenv(envVarName);
        }
        return apiKey;
    }

    /**
     * 根據提供者 ID 取得對應的環境變數名稱
     */
    protected String getEnvVarName(String providerId) {
        return switch (providerId.toLowerCase()) {
            case "openai" -> "OPENAI_API_KEY";
            case "claude", "anthropic" -> "ANTHROPIC_API_KEY";
            case "gemini", "google" -> "GOOGLE_API_KEY";
            default -> providerId.toUpperCase() + "_API_KEY";
        };
    }

    /**
     * 建立 AI Provider 設定
     */
    protected AiProviderSettings buildProviderSettings(
        Map<String, Object> credential,
        String providerId
    ) {
        String apiKey = resolveApiKey(credential, getEnvVarName(providerId));
        String baseUrl = getCredentialValue(credential, "baseUrl");

        AiProviderSettings.AiProviderSettingsBuilder builder = AiProviderSettings.builder();

        if (apiKey != null && !apiKey.isEmpty()) {
            builder.apiKey(apiKey);
        }
        if (baseUrl != null && !baseUrl.isEmpty()) {
            builder.baseUrl(baseUrl);
        }

        return builder.build();
    }

    /**
     * 記錄 Token 使用量（供計費/配額管理）
     */
    protected void recordTokenUsage(
        UUID userId,
        String provider,
        String model,
        int inputTokens,
        int outputTokens
    ) {
        log.debug("Token usage - user: {}, provider: {}, model: {}, input: {}, output: {}",
            userId, provider, model, inputTokens, outputTokens);
        // TODO: 實作 Token 使用量記錄到資料庫
    }

    /**
     * 建立 AiChatRequest
     */
    protected AiChatRequest buildChatRequest(
        String model,
        String systemPrompt,
        String userPrompt,
        double temperature,
        int maxTokens,
        Map<String, Object> providerOptions
    ) {
        AiChatRequest.AiChatRequestBuilder builder = AiChatRequest.builder()
            .model(model)
            .temperature(temperature)
            .maxTokens(maxTokens);

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            builder.systemPrompt(systemPrompt);
        }

        if (userPrompt != null) {
            builder.messages(java.util.List.of(AiMessage.user(userPrompt)));
        }

        if (providerOptions != null && !providerOptions.isEmpty()) {
            builder.providerOptions(providerOptions);
        }

        return builder.build();
    }

    // ===== 抽象方法 =====

    /**
     * 是否支援串流輸出
     */
    @Override
    public abstract boolean supportsStreaming();

    /**
     * 串流執行（子類需實作）
     */
    @Override
    public abstract Flux<StreamChunk> executeStream(NodeExecutionContext context);
}
