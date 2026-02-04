package com.aiinpocket.n3n.ai.agent;

import lombok.Builder;
import lombok.Data;
import java.util.*;

/**
 * Agent 執行上下文
 * 在整個多代理協作過程中共享
 */
@Data
@Builder
public class AgentContext {

    /** 對話 ID */
    private UUID conversationId;

    /** 使用者 ID */
    private UUID userId;

    /** 流程 ID */
    private String flowId;

    /** 原始使用者輸入 */
    private String userInput;

    /** 解析後的意圖 */
    private Intent intent;

    /** 當前工作流程草稿 */
    private WorkingFlowDraft flowDraft;

    /** 對話歷史（最近 N 則） */
    @Builder.Default
    private List<Message> conversationHistory = new ArrayList<>();

    /** 工作記憶（跨 Agent 共享） */
    @Builder.Default
    private Map<String, Object> workingMemory = new HashMap<>();

    /** 工具執行結果歷史 */
    @Builder.Default
    private List<ToolResult> toolResults = new ArrayList<>();

    /** 已使用的 Agent 列表（避免循環） */
    @Builder.Default
    private Set<String> visitedAgents = new HashSet<>();

    /** 迭代計數（防止無限迴圈） */
    @Builder.Default
    private int iterationCount = 0;

    /** 最大迭代次數 */
    @Builder.Default
    private int maxIterations = 10;

    /** 元件上下文快照 */
    private Map<String, Object> componentContext;

    /** 技能上下文快照 */
    private Map<String, Object> skillContext;

    /** 當前流程的節點（用於修改） */
    private List<Map<String, Object>> currentNodes;

    /** 當前流程的邊（用於修改） */
    private List<Map<String, Object>> currentEdges;

    /**
     * 檢查是否可繼續迭代
     */
    public boolean canContinue() {
        return iterationCount < maxIterations;
    }

    /**
     * 新增工具結果
     */
    public void addToolResult(ToolResult result) {
        if (toolResults == null) {
            toolResults = new ArrayList<>();
        }
        toolResults.add(result);
    }

    /**
     * 從工作記憶取得值
     */
    @SuppressWarnings("unchecked")
    public <T> T getFromMemory(String key, Class<T> type) {
        if (workingMemory == null) return null;
        return (T) workingMemory.get(key);
    }

    /**
     * 存入工作記憶
     */
    public void setInMemory(String key, Object value) {
        if (workingMemory == null) {
            workingMemory = new HashMap<>();
        }
        workingMemory.put(key, value);
    }

    /**
     * 增加迭代計數
     */
    public void incrementIteration() {
        this.iterationCount++;
    }

    /**
     * 標記訪問過的 Agent
     */
    public void markVisited(String agentId) {
        if (visitedAgents == null) {
            visitedAgents = new HashSet<>();
        }
        visitedAgents.add(agentId);
    }
}
