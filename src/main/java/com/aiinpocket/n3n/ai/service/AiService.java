package com.aiinpocket.n3n.ai.service;

import com.aiinpocket.n3n.ai.embedding.EmbeddingService;
import com.aiinpocket.n3n.ai.module.SimpleAIProvider;
import com.aiinpocket.n3n.ai.module.SimpleAIProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * AI 服務
 *
 * 提供基礎的 AI 操作，包括文字生成和向量嵌入。
 * 整合 SimpleAIProviderRegistry 來支援多種 AI 提供者。
 * 向量嵌入透過 EmbeddingService 提供（支援 OpenAI、Ollama 等）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AiService {

    private final SimpleAIProviderRegistry providerRegistry;
    private final EmbeddingService embeddingService;

    /**
     * 產生文字回應
     *
     * @param prompt 提示詞
     * @param model 模型名稱（如 gpt-4, gpt-3.5-turbo 等）
     * @return 產生的文字
     */
    public String generateText(String prompt, String model) {
        SimpleAIProvider provider = providerRegistry.getDefaultProvider();
        if (provider == null) {
            throw new IllegalStateException("No AI provider configured");
        }

        try {
            var messages = List.of(
                    Map.of("role", "user", "content", prompt)
            );

            String response = provider.chat(messages, model);
            log.debug("Generated text using model {}: {} chars", model, response.length());
            return response;

        } catch (Exception e) {
            log.error("Failed to generate text with model {}", model, e);
            throw new RuntimeException("Text generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * 產生文字回應（使用預設模型）
     *
     * @param prompt 提示詞
     * @return 產生的文字
     */
    public String generateText(String prompt) {
        return generateText(prompt, "gpt-3.5-turbo");
    }

    /**
     * 取得文字的向量嵌入
     *
     * 使用配置的 EmbeddingService（OpenAI、Ollama 或 Simple）來產生向量嵌入。
     *
     * @param text 文字內容
     * @return 向量嵌入（維度取決於使用的模型，OpenAI 通常為 1536）
     */
    public float[] getEmbedding(String text) {
        try {
            float[] embedding = embeddingService.getEmbedding(text);
            log.debug("Generated embedding using {} ({}): {} dimensions",
                    embeddingService.getProviderName(),
                    embeddingService.getModelName(),
                    embedding.length);
            return embedding;

        } catch (Exception e) {
            log.error("Failed to get embedding from {}", embeddingService.getProviderName(), e);
            throw new RuntimeException("Embedding generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * 取得向量維度
     *
     * @return 向量維度
     */
    public int getEmbeddingDimension() {
        return embeddingService.getDimension();
    }

    /**
     * 計算兩個向量之間的餘弦相似度
     *
     * @param embedding1 第一個向量
     * @param embedding2 第二個向量
     * @return 相似度分數（-1 到 1 之間）
     */
    public float cosineSimilarity(float[] embedding1, float[] embedding2) {
        return embeddingService.cosineSimilarity(embedding1, embedding2);
    }

    /**
     * 取得嵌入服務資訊
     *
     * @return 提供者名稱
     */
    public String getEmbeddingProviderInfo() {
        return embeddingService.getProviderName() + "/" + embeddingService.getModelName();
    }

    /**
     * 批次取得文字的向量嵌入
     *
     * 批次處理可以減少 API 呼叫次數，提升效能。
     *
     * @param texts 文字列表
     * @return 向量嵌入列表
     */
    public List<float[]> getEmbeddings(List<String> texts) {
        try {
            return embeddingService.getBatchEmbeddings(texts);
        } catch (Exception e) {
            log.error("Failed to get batch embeddings, falling back to sequential", e);
            return texts.stream()
                    .map(this::getEmbedding)
                    .toList();
        }
    }

    /**
     * 估算文字的 token 數量
     *
     * @param text 文字內容
     * @return 估算的 token 數量
     */
    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // 簡化估算：英文約 4 字元/token，中文約 2 字元/token
        // 這裡使用平均估算
        return Math.max(1, text.length() / 3);
    }

    /**
     * 檢查 AI 服務是否可用
     *
     * @return true 如果有可用的 AI 提供者
     */
    public boolean isAvailable() {
        return providerRegistry.getDefaultProvider() != null;
    }
}
