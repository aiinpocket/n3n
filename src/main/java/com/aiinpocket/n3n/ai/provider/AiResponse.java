package com.aiinpocket.n3n.ai.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
     * 停止原因: end_turn, max_tokens, stop_sequence, tool_use
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

    /**
     * 工具調用列表（當 AI 決定調用工具時）
     */
    private List<AiToolCall> toolCalls;
}
