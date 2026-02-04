package com.aiinpocket.n3n.ai.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * AI 聊天訊息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiMessage {

    /**
     * 訊息角色: system, user, assistant, tool
     */
    private String role;

    /**
     * 文字內容
     */
    private String content;

    /**
     * 多模態內容（用於圖片等）
     */
    private List<AiContent> multiContent;

    /**
     * 工具調用列表（assistant 訊息使用）
     */
    private List<AiToolCall> toolCalls;

    /**
     * 工具調用 ID（tool 角色訊息使用，對應 assistant 的工具調用）
     */
    private String toolCallId;

    public static AiMessage system(String content) {
        return AiMessage.builder().role("system").content(content).build();
    }

    public static AiMessage user(String content) {
        return AiMessage.builder().role("user").content(content).build();
    }

    public static AiMessage assistant(String content) {
        return AiMessage.builder().role("assistant").content(content).build();
    }

    public static AiMessage assistant(String content, List<AiToolCall> toolCalls) {
        return AiMessage.builder()
            .role("assistant")
            .content(content)
            .toolCalls(toolCalls)
            .build();
    }

    public static AiMessage toolResult(String toolCallId, String content) {
        return AiMessage.builder()
            .role("tool")
            .toolCallId(toolCallId)
            .content(content)
            .build();
    }
}
