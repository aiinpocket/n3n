package com.aiinpocket.n3n.ai.memory;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Memory 訊息模型
 *
 * 代表對話中的一個訊息，可以是使用者輸入或 AI 回應。
 */
@Data
@Builder
public class MemoryMessage {

    /**
     * 訊息 ID
     */
    private String id;

    /**
     * 對話 ID
     */
    private String conversationId;

    /**
     * 角色：user, assistant, system, function
     */
    private String role;

    /**
     * 訊息內容
     */
    private String content;

    /**
     * 建立時間
     */
    private Instant createdAt;

    /**
     * Token 數量（估算）
     */
    private Integer tokenCount;

    /**
     * 額外元資料
     */
    private Map<String, Object> metadata;

    /**
     * 訊息嵌入向量（用於 Vector Memory）
     */
    private float[] embedding;

    /**
     * 建立新的使用者訊息
     */
    public static MemoryMessage userMessage(String conversationId, String content) {
        return MemoryMessage.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .role("user")
                .content(content)
                .createdAt(Instant.now())
                .tokenCount(estimateTokens(content))
                .build();
    }

    /**
     * 建立新的 AI 回應訊息
     */
    public static MemoryMessage assistantMessage(String conversationId, String content) {
        return MemoryMessage.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .role("assistant")
                .content(content)
                .createdAt(Instant.now())
                .tokenCount(estimateTokens(content))
                .build();
    }

    /**
     * 建立系統訊息
     */
    public static MemoryMessage systemMessage(String conversationId, String content) {
        return MemoryMessage.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .role("system")
                .content(content)
                .createdAt(Instant.now())
                .tokenCount(estimateTokens(content))
                .build();
    }

    /**
     * 估算 token 數量（簡化版，約每 4 字元 1 token）
     */
    private static int estimateTokens(String content) {
        if (content == null) return 0;
        return Math.max(1, content.length() / 4);
    }
}
