package com.aiinpocket.n3n.ai.provider;

import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI Provider 核心介面
 * 所有 AI 供應商實作必須實現此介面
 */
public interface AiProvider {

    /**
     * 取得供應商唯一識別碼
     */
    String getProviderId();

    /**
     * 取得供應商顯示名稱
     */
    String getDisplayName();

    /**
     * 取得預設 API Base URL
     */
    String getDefaultBaseUrl();

    /**
     * 取得預設超時時間（毫秒）
     */
    int getDefaultTimeoutMs();

    /**
     * 動態取得可用模型清單
     * @param apiKey API 金鑰
     * @param baseUrl 自訂 Base URL（可為 null 使用預設）
     * @return 可用模型清單
     */
    CompletableFuture<List<AiModel>> fetchModels(String apiKey, String baseUrl);

    /**
     * 發送聊天請求（非串流）
     * @param request 聊天請求
     * @param settings 供應商設定
     * @return AI 回應
     */
    CompletableFuture<AiResponse> chat(AiChatRequest request, AiProviderSettings settings);

    /**
     * 發送聊天請求（串流）
     * @param request 聊天請求
     * @param settings 供應商設定
     * @return 串流回應
     */
    Flux<AiStreamChunk> chatStream(AiChatRequest request, AiProviderSettings settings);

    /**
     * 測試連線
     * @param apiKey API 金鑰
     * @param baseUrl 自訂 Base URL
     * @return 連線是否成功
     */
    CompletableFuture<Boolean> testConnection(String apiKey, String baseUrl);

    /**
     * 生成文本嵌入向量
     * @param request 嵌入請求
     * @param settings 供應商設定
     * @return 嵌入回應
     */
    default CompletableFuture<AiEmbeddingResponse> embed(AiEmbeddingRequest request, AiProviderSettings settings) {
        // 預設實作 - 不支援 embedding 的供應商會拋出例外
        return CompletableFuture.failedFuture(
            new UnsupportedOperationException("Embedding not supported by this provider: " + getProviderId())
        );
    }

    /**
     * 取得供應商特定設定 Schema（JSON Schema 格式）
     */
    Map<String, Object> getConfigSchema();

    /**
     * 是否需要 API Key
     */
    default boolean requiresApiKey() {
        return true;
    }
}
