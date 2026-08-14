package com.aiinpocket.n3n.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 對話串流請求
 */
@Data
public class ChatStreamRequest {

    /**
     * 使用者訊息
     */
    @NotBlank
    @Size(max = 10000)
    private String message;

    /**
     * 對話 ID（可選，用於繼續對話）
     */
    private UUID conversationId;

    /**
     * 流程 ID（可選，用於流程相關操作）
     */
    private String flowId;

    /**
     * 當前流程定義（可選，用於修改現有流程）
     */
    private FlowDefinition flowDefinition;

    /**
     * 執行 ID（可選）：帶入時 AI 會取得該次執行的節點結果與錯誤，
     * 用於「執行失敗分析小幫手」情境
     */
    private UUID executionId;

    @Data
    public static class FlowDefinition {
        private List<Map<String, Object>> nodes;
        private List<Map<String, Object>> edges;
    }
}
