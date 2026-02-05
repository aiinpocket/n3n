package com.aiinpocket.n3n.ai.memory;

import lombok.Builder;
import lombok.Data;

/**
 * Memory 配置
 *
 * 定義 Memory 的行為參數。
 */
@Data
@Builder
public class MemoryConfig {

    /**
     * Memory 類型
     */
    @Builder.Default
    private MemoryType type = MemoryType.BUFFER;

    /**
     * 最大 token 數量
     * 超過此數量時會根據 Memory 類型進行處理（截斷、摘要等）
     */
    @Builder.Default
    private int maxTokens = 4000;

    /**
     * Window Memory: 保留的訊息數量
     */
    @Builder.Default
    private int windowSize = 10;

    /**
     * Summary Memory: 摘要時使用的 AI 模型
     */
    private String summaryModel;

    /**
     * Summary Memory: 觸發摘要的 token 閾值
     */
    @Builder.Default
    private int summaryThreshold = 3000;

    /**
     * Vector Memory: 向量維度
     */
    @Builder.Default
    private int vectorDimension = 1536;

    /**
     * Vector Memory: 搜尋時返回的最大結果數
     */
    @Builder.Default
    private int vectorTopK = 5;

    /**
     * Vector Memory: 相似度閾值 (0-1)
     */
    @Builder.Default
    private float vectorSimilarityThreshold = 0.7f;

    /**
     * Entity Memory: 實體提取模型
     */
    private String entityExtractionModel;

    /**
     * 是否包含系統訊息在記憶中
     */
    @Builder.Default
    private boolean includeSystemMessages = false;

    /**
     * 訊息過期時間（秒），0 表示永不過期
     */
    @Builder.Default
    private long ttlSeconds = 0;

    /**
     * 預設配置（Buffer Memory）
     */
    public static MemoryConfig defaultConfig() {
        return MemoryConfig.builder().build();
    }

    /**
     * Window Memory 配置
     */
    public static MemoryConfig windowConfig(int windowSize) {
        return MemoryConfig.builder()
                .type(MemoryType.WINDOW)
                .windowSize(windowSize)
                .build();
    }

    /**
     * Summary Memory 配置
     */
    public static MemoryConfig summaryConfig(String model, int threshold) {
        return MemoryConfig.builder()
                .type(MemoryType.SUMMARY)
                .summaryModel(model)
                .summaryThreshold(threshold)
                .build();
    }

    /**
     * Vector Memory 配置
     */
    public static MemoryConfig vectorConfig(int dimension, int topK) {
        return MemoryConfig.builder()
                .type(MemoryType.VECTOR)
                .vectorDimension(dimension)
                .vectorTopK(topK)
                .build();
    }
}
