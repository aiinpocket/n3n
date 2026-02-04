package com.aiinpocket.n3n.ai.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI Embedding 請求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiEmbeddingRequest {

    /**
     * 模型識別碼
     */
    private String model;

    /**
     * 要嵌入的文本列表
     */
    private List<String> inputs;

    /**
     * 單一文本（便捷方式）
     */
    private String input;

    /**
     * 嵌入維度（可選，某些模型支援）
     */
    private Integer dimensions;

    /**
     * 建立單一文本嵌入請求
     */
    public static AiEmbeddingRequest of(String model, String input) {
        return AiEmbeddingRequest.builder()
                .model(model)
                .input(input)
                .build();
    }

    /**
     * 建立批次嵌入請求
     */
    public static AiEmbeddingRequest of(String model, List<String> inputs) {
        return AiEmbeddingRequest.builder()
                .model(model)
                .inputs(inputs)
                .build();
    }

    /**
     * 取得要處理的文本列表
     */
    public List<String> getTexts() {
        if (inputs != null && !inputs.isEmpty()) {
            return inputs;
        }
        if (input != null) {
            return List.of(input);
        }
        return List.of();
    }
}
