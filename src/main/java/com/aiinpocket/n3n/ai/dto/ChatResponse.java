package com.aiinpocket.n3n.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 對話回應
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 對話 ID
     */
    private UUID conversationId;

    /**
     * AI 回應內容
     */
    private String content;

    /**
     * 流程定義（如果有更新）
     */
    private Map<String, Object> flowDefinition;

    /**
     * 待確認的變更
     */
    private List<PendingChange> pendingChanges;

    /**
     * 錯誤訊息
     */
    private String error;

    @Data
    @Builder
    public static class PendingChange {
        private String id;
        private String type;
        private String description;
        private Map<String, Object> before;
        private Map<String, Object> after;
    }

    /**
     * 建立成功回應
     */
    public static ChatResponse success(UUID conversationId, String content) {
        return ChatResponse.builder()
            .success(true)
            .conversationId(conversationId)
            .content(content)
            .build();
    }

    /**
     * 建立成功回應（含流程更新）
     */
    public static ChatResponse successWithFlow(
            UUID conversationId,
            String content,
            Map<String, Object> flowDefinition,
            List<PendingChange> pendingChanges) {
        return ChatResponse.builder()
            .success(true)
            .conversationId(conversationId)
            .content(content)
            .flowDefinition(flowDefinition)
            .pendingChanges(pendingChanges)
            .build();
    }

    /**
     * 建立錯誤回應
     */
    public static ChatResponse error(String errorMessage) {
        return ChatResponse.builder()
            .success(false)
            .error(errorMessage)
            .build();
    }
}
