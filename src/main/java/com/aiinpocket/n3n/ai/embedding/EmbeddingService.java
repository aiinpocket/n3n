package com.aiinpocket.n3n.ai.embedding;

import java.util.List;

/**
 * 向量嵌入服務介面
 *
 * 提供文字轉向量嵌入的功能，支援多種 AI 提供者（OpenAI、Ollama 等）。
 * 用於語義搜尋、相似度比較、RAG 等應用場景。
 */
public interface EmbeddingService {

    /**
     * 取得單一文字的向量嵌入
     *
     * @param text 要嵌入的文字
     * @return 向量嵌入陣列
     */
    float[] getEmbedding(String text);

    /**
     * 批次取得多個文字的向量嵌入
     *
     * 批次處理可以減少 API 呼叫次數，提升效能。
     *
     * @param texts 要嵌入的文字列表
     * @return 向量嵌入列表，順序與輸入相同
     */
    List<float[]> getBatchEmbeddings(List<String> texts);

    /**
     * 取得向量維度
     *
     * @return 向量維度（例如 OpenAI text-embedding-3-small 為 1536）
     */
    int getDimension();

    /**
     * 取得提供者名稱
     *
     * @return 提供者名稱（例如 "openai", "ollama"）
     */
    String getProviderName();

    /**
     * 取得使用的模型名稱
     *
     * @return 模型名稱（例如 "text-embedding-3-small"）
     */
    String getModelName();

    /**
     * 檢查服務是否可用
     *
     * @return true 如果服務已配置且可用
     */
    boolean isAvailable();

    /**
     * 計算兩個向量之間的餘弦相似度
     *
     * @param embedding1 第一個向量
     * @param embedding2 第二個向量
     * @return 相似度分數（-1 到 1 之間，1 表示完全相同）
     */
    default float cosineSimilarity(float[] embedding1, float[] embedding2) {
        if (embedding1 == null || embedding2 == null || embedding1.length != embedding2.length) {
            return 0f;
        }

        float dotProduct = 0f;
        float norm1 = 0f;
        float norm2 = 0f;

        for (int i = 0; i < embedding1.length; i++) {
            dotProduct += embedding1[i] * embedding2[i];
            norm1 += embedding1[i] * embedding1[i];
            norm2 += embedding2[i] * embedding2[i];
        }

        float denominator = (float) (Math.sqrt(norm1) * Math.sqrt(norm2));
        if (denominator == 0) {
            return 0f;
        }

        return dotProduct / denominator;
    }
}
