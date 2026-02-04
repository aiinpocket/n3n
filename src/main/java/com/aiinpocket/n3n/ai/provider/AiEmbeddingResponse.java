package com.aiinpocket.n3n.ai.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * AI Embedding 回應
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEmbeddingResponse {

    /**
     * 嵌入結果列表（每個嵌入是浮點數向量）
     */
    private List<List<Float>> embeddings;

    /**
     * 使用的模型
     */
    private String model;

    /**
     * Token 使用量
     */
    private AiUsage usage;

    /**
     * 取得第一個嵌入向量（便捷方法）
     */
    public List<Float> getFirstEmbedding() {
        if (embeddings != null && !embeddings.isEmpty()) {
            return embeddings.get(0);
        }
        return null;
    }

    /**
     * 取得嵌入向量維度
     */
    public int getDimensions() {
        if (embeddings != null && !embeddings.isEmpty() && embeddings.get(0) != null) {
            return embeddings.get(0).size();
        }
        return 0;
    }

    /**
     * 建立單一嵌入回應
     */
    public static AiEmbeddingResponse ofSingle(String model, List<Float> embedding) {
        return AiEmbeddingResponse.builder()
                .model(model)
                .embeddings(List.of(embedding))
                .build();
    }

    /**
     * 建立批次嵌入回應
     */
    public static AiEmbeddingResponse ofBatch(String model, List<List<Float>> embeddings) {
        return AiEmbeddingResponse.builder()
                .model(model)
                .embeddings(embeddings)
                .build();
    }

    /**
     * 從 Double 列表轉換（某些 API 回傳 Double）
     */
    public static List<Float> toFloatList(List<Double> doubles) {
        if (doubles == null) return null;
        List<Float> floats = new ArrayList<>(doubles.size());
        for (Double d : doubles) {
            floats.add(d.floatValue());
        }
        return floats;
    }
}
