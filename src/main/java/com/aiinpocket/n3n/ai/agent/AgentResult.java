package com.aiinpocket.n3n.ai.agent;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * Agent 執行結果
 */
@Data
@Builder
public class AgentResult {

    /** 是否成功 */
    private boolean success;

    /** 回應內容（文字） */
    private String content;

    /** 錯誤訊息 */
    private String error;

    /** 結構化資料 */
    private Map<String, Object> data;

    /** 流程定義（如果有建立或修改流程） */
    private Map<String, Object> flowDefinition;

    /** 推薦列表 */
    private List<Map<String, Object>> recommendations;

    /** 是否需要後續處理 */
    @Builder.Default
    private boolean requiresFollowUp = false;

    /** 下一個要執行的 Agent ID */
    private String nextAction;

    /** 待確認的變更 */
    private List<PendingChange> pendingChanges;

    /**
     * 建立成功結果
     */
    public static AgentResult success(String content) {
        return AgentResult.builder()
            .success(true)
            .content(content)
            .build();
    }

    /**
     * 建立成功結果（含流程定義）
     */
    public static AgentResult successWithFlow(String content, Map<String, Object> flowDefinition) {
        return AgentResult.builder()
            .success(true)
            .content(content)
            .flowDefinition(flowDefinition)
            .build();
    }

    /**
     * 建立錯誤結果
     */
    public static AgentResult error(String message) {
        return AgentResult.builder()
            .success(false)
            .error(message)
            .build();
    }

    /**
     * 建立需要後續處理的結果
     */
    public static AgentResult needsFollowUp(String content, String nextAction) {
        return AgentResult.builder()
            .success(true)
            .content(content)
            .requiresFollowUp(true)
            .nextAction(nextAction)
            .build();
    }

    /**
     * 待確認的變更
     */
    @Data
    @Builder
    public static class PendingChange {
        private String id;
        private String type; // add_node, remove_node, modify_node, connect_nodes
        private String description;
        private Map<String, Object> before;
        private Map<String, Object> after;
    }
}
