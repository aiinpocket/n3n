package com.aiinpocket.n3n.ai.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 串流回應片段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiStreamChunk {

    /**
     * 回應識別碼
     */
    private String id;

    /**
     * 增量內容
     */
    private String delta;

    /**
     * 停止原因（完成時才有值）
     */
    private String stopReason;

    /**
     * 是否完成
     */
    private boolean done;

    /**
     * Token 使用量（完成時才有）
     */
    private AiUsage usage;

    /**
     * 工具調用（串流中逐漸構建）
     */
    private List<AiToolCall> toolCalls;

    public static AiStreamChunk text(String delta) {
        return AiStreamChunk.builder()
                .delta(delta)
                .done(false)
                .build();
    }

    public static AiStreamChunk done(String stopReason, AiUsage usage) {
        return AiStreamChunk.builder()
                .done(true)
                .stopReason(stopReason)
                .usage(usage)
                .build();
    }

    public static AiStreamChunk toolCall(List<AiToolCall> toolCalls) {
        return AiStreamChunk.builder()
                .toolCalls(toolCalls)
                .done(false)
                .build();
    }
}
