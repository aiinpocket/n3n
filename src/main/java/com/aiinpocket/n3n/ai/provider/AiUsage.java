package com.aiinpocket.n3n.ai.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI Token 使用量
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsage {

    /**
     * 輸入 Token 數
     */
    private int inputTokens;

    /**
     * 輸出 Token 數
     */
    private int outputTokens;

    /**
     * 總 Token 數
     */
    private int totalTokens;

    public static AiUsage of(int input, int output) {
        return AiUsage.builder()
                .inputTokens(input)
                .outputTokens(output)
                .totalTokens(input + output)
                .build();
    }
}
