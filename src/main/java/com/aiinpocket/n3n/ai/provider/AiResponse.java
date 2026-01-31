package com.aiinpocket.n3n.ai.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 聊天回應
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiResponse {

    /**
     * 回應識別碼
     */
    private String id;

    /**
     * 使用的模型
     */
    private String model;

    /**
     * 回應內容
     */
    private String content;

    /**
     * 停止原因: end_turn, max_tokens, stop_sequence
     */
    private String stopReason;

    /**
     * Token 使用量
     */
    private AiUsage usage;

    /**
     * 延遲（毫秒）
     */
    private long latencyMs;
}
