package com.aiinpocket.n3n.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 需求釐清請求 DTO
 * 支持多輪對話式需求釐清
 */
@Data
public class RequirementClarificationRequest {

    /**
     * 使用者最新訊息
     */
    @NotBlank
    @Size(max = 5000)
    private String message;

    /**
     * 對話 ID（繼續已有對話時提供）
     */
    private UUID conversationId;

    /**
     * 對話歷史（前端維護，每次傳送完整歷史）
     */
    private List<ChatMessage> history;

    /**
     * 語言偏好
     */
    private String language;

    @Data
    public static class ChatMessage {
        private String role; // user | assistant
        private String content;
    }
}
