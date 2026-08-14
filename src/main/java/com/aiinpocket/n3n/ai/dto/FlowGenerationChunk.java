package com.aiinpocket.n3n.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 流程生成串流回應片段
 * 用於 SSE 傳輸，支援即時預覽
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowGenerationChunk {

    /**
     * 片段類型
     * - thinking: AI 思考中訊息
     * - progress: 進度更新
     * - understanding: AI 理解的需求摘要
     * - node_added: 新增節點
     * - edge_added: 新增連線
     * - missing_nodes: 缺失的節點類型
     * - done: 完成
     * - error: 錯誤
     */
    private String type;

    /**
     * 進度百分比 (0-100)
     */
    private Integer progress;

    /**
     * 當前階段描述
     */
    private String stage;

    /**
     * 文字訊息
     */
    private String message;

    /**
     * 節點資料 (用於 node_added)
     */
    private NodeData node;

    /**
     * 連線資料 (用於 edge_added)
     */
    private EdgeData edge;

    /**
     * 缺失節點列表 (用於 missing_nodes)
     */
    private List<MissingNodeInfo> missingNodes;

    /**
     * 完整流程定義 (用於 done)
     */
    private Map<String, Object> flowDefinition;

    /**
     * 所需節點類型列表
     */
    private List<String> requiredNodes;

    /**
     * 背景驗證結果（type=node_probed 時）：
     * {nodeId, status, message?, durationMs?, outputSample?}
     */
    private Map<String, Object> probe;

    /**
     * 需要使用者提供資訊／確認副作用（type=node_input_required 時）：
     * {sessionId, nodeId, nodeLabel, nodeType, reason, sideEffect, config}
     */
    private Map<String, Object> inputRequest;

    /**
     * 時間戳記
     */
    @Builder.Default
    private String timestamp = Instant.now().toString();

    // ========== 工廠方法 ==========

    /**
     * 建立思考中片段
     */
    public static FlowGenerationChunk thinking(String message) {
        return FlowGenerationChunk.builder()
                .type("thinking")
                .message(message)
                .build();
    }

    /**
     * 建立進度更新片段
     */
    public static FlowGenerationChunk progress(int percent, String stage) {
        return FlowGenerationChunk.builder()
                .type("progress")
                .progress(percent)
                .stage(stage)
                .build();
    }

    /**
     * 建立理解摘要片段
     */
    public static FlowGenerationChunk understanding(String understanding) {
        return FlowGenerationChunk.builder()
                .type("understanding")
                .message(understanding)
                .build();
    }

    /**
     * 建立節點新增片段
     */
    public static FlowGenerationChunk nodeAdded(NodeData node) {
        return FlowGenerationChunk.builder()
                .type("node_added")
                .node(node)
                .build();
    }

    /**
     * 建立連線新增片段
     */
    public static FlowGenerationChunk edgeAdded(EdgeData edge) {
        return FlowGenerationChunk.builder()
                .type("edge_added")
                .edge(edge)
                .build();
    }

    /**
     * 建立缺失節點片段
     */
    public static FlowGenerationChunk missingNodes(List<MissingNodeInfo> missing) {
        return FlowGenerationChunk.builder()
                .type("missing_nodes")
                .missingNodes(missing)
                .build();
    }

    /**
     * 建立完成片段
     */
    public static FlowGenerationChunk done(Map<String, Object> flowDefinition, List<String> requiredNodes) {
        return FlowGenerationChunk.builder()
                .type("done")
                .flowDefinition(flowDefinition)
                .requiredNodes(requiredNodes)
                .build();
    }

    /**
     * 建立錯誤片段
     */
    /**
     * 背景驗證（節點試打）結果事件。
     * probe: {nodeId, status: verified|needsInput|skipped|failed, message?, durationMs?, outputSample?}
     */
    public static FlowGenerationChunk nodeProbed(Map<String, Object> probe) {
        return FlowGenerationChunk.builder()
                .type("node_probed")
                .flowDefinition(null)
                .probe(probe)
                .build();
    }

    /**
     * 背景驗證需要使用者提供資訊／確認副作用的詢問事件。
     */
    public static FlowGenerationChunk nodeInputRequired(Map<String, Object> inputRequest) {
        return FlowGenerationChunk.builder()
                .type("node_input_required")
                .inputRequest(inputRequest)
                .build();
    }

    public static FlowGenerationChunk error(String message) {
        return FlowGenerationChunk.builder()
                .type("error")
                .message(message)
                .build();
    }

    // ========== 巢狀資料類別 ==========

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NodeData {
        private String id;
        private String type;
        private String label;
        private Map<String, Object> config;
        private Position position;
    }

    @Data
    @Builder
    public static class Position {
        private double x;
        private double y;
    }

    @Data
    @Builder
    public static class EdgeData {
        private String id;
        private String source;
        private String target;
        private String label;
    }

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MissingNodeInfo {
        private String nodeType;
        private String displayName;
        private String description;
        private String pluginId;        // 可安裝的 Plugin ID
        private boolean canAutoInstall; // 是否可自動安裝
    }
}
