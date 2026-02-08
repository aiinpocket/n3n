package com.aiinpocket.n3n.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * 需求釐清回應 DTO
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RequirementClarificationResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 對話 ID
     */
    private UUID conversationId;

    /**
     * AI 回應訊息（可能是追問或確認）
     */
    private String message;

    /**
     * 是否需求已完整（true = 可以進入生成階段）
     */
    private boolean requirementComplete;

    /**
     * 需求摘要（當 requirementComplete=true 時提供）
     */
    private RequirementSummary summary;

    /**
     * 建議的快速回答選項（讓用戶可快速選擇）
     */
    private List<String> suggestedReplies;

    /**
     * 錯誤訊息
     */
    private String error;

    @Data
    @Builder
    public static class RequirementSummary {
        private String triggerType;        // 觸發方式
        private String triggerDescription; // 觸發描述
        private String dataSource;         // 數據來源
        private List<String> processSteps; // 處理步驟
        private String outputTarget;       // 輸出目的
        private String errorHandling;      // 錯誤處理策略
        private String fullDescription;    // 完整需求描述
    }

    public static RequirementClarificationResponse question(UUID conversationId, String message, List<String> suggestions) {
        return RequirementClarificationResponse.builder()
            .success(true)
            .conversationId(conversationId)
            .message(message)
            .requirementComplete(false)
            .suggestedReplies(suggestions)
            .build();
    }

    public static RequirementClarificationResponse complete(UUID conversationId, String message, RequirementSummary summary) {
        return RequirementClarificationResponse.builder()
            .success(true)
            .conversationId(conversationId)
            .message(message)
            .requirementComplete(true)
            .summary(summary)
            .build();
    }

    public static RequirementClarificationResponse error(String errorMessage) {
        return RequirementClarificationResponse.builder()
            .success(false)
            .error(errorMessage)
            .build();
    }
}
