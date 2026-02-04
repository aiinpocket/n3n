package com.aiinpocket.n3n.ai.agent.tools;

import com.aiinpocket.n3n.ai.agent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 連接節點工具
 * 在流程草稿中建立節點之間的連線
 */
@Slf4j
@Component
public class ConnectNodesTool implements AgentTool {

    @Override
    public String getName() {
        return "connect_nodes";
    }

    @Override
    public String getDescription() {
        return "連接兩個節點。建立從來源節點到目標節點的資料流。";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "sourceId", Map.of(
                    "type", "string",
                    "description", "來源節點 ID"
                ),
                "targetId", Map.of(
                    "type", "string",
                    "description", "目標節點 ID"
                ),
                "sourceLabel", Map.of(
                    "type", "string",
                    "description", "來源節點名稱（如果不知道 ID）"
                ),
                "targetLabel", Map.of(
                    "type", "string",
                    "description", "目標節點名稱（如果不知道 ID）"
                )
            ),
            "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, AgentContext context) {
        long startTime = System.currentTimeMillis();
        String sourceId = (String) parameters.get("sourceId");
        String targetId = (String) parameters.get("targetId");
        String sourceLabel = (String) parameters.get("sourceLabel");
        String targetLabel = (String) parameters.get("targetLabel");

        log.debug("Connecting nodes: {} -> {}", sourceId != null ? sourceId : sourceLabel,
            targetId != null ? targetId : targetLabel);

        try {
            WorkingFlowDraft draft = context.getFlowDraft();
            if (draft == null || !draft.hasContent()) {
                return ToolResult.failure(getName(), "沒有可操作的流程草稿");
            }

            // 解析來源 ID
            String resolvedSourceId = sourceId;
            if (resolvedSourceId == null && sourceLabel != null) {
                resolvedSourceId = findNodeIdByLabel(draft, sourceLabel);
                if (resolvedSourceId == null) {
                    return ToolResult.failure(getName(),
                        "找不到名稱為 '" + sourceLabel + "' 的來源節點");
                }
            }

            // 解析目標 ID
            String resolvedTargetId = targetId;
            if (resolvedTargetId == null && targetLabel != null) {
                resolvedTargetId = findNodeIdByLabel(draft, targetLabel);
                if (resolvedTargetId == null) {
                    return ToolResult.failure(getName(),
                        "找不到名稱為 '" + targetLabel + "' 的目標節點");
                }
            }

            if (resolvedSourceId == null || resolvedTargetId == null) {
                return ToolResult.failure(getName(),
                    "必須提供來源和目標節點的 ID 或名稱");
            }

            // 驗證節點存在
            if (draft.getNode(resolvedSourceId).isEmpty()) {
                return ToolResult.failure(getName(),
                    "來源節點 '" + resolvedSourceId + "' 不存在");
            }
            if (draft.getNode(resolvedTargetId).isEmpty()) {
                return ToolResult.failure(getName(),
                    "目標節點 '" + resolvedTargetId + "' 不存在");
            }

            // 防止自連接
            if (resolvedSourceId.equals(resolvedTargetId)) {
                return ToolResult.failure(getName(), "不能將節點連接到自己");
            }

            // 執行連接
            draft.connectNodes(resolvedSourceId, resolvedTargetId);

            long duration = System.currentTimeMillis() - startTime;
            return ToolResult.builder()
                .toolName(getName())
                .success(true)
                .data(Map.of(
                    "sourceId", resolvedSourceId,
                    "targetId", resolvedTargetId,
                    "edgeCount", draft.getEdgeCount(),
                    "flowDraft", draft.toDefinition()
                ))
                .durationMs(duration)
                .build();

        } catch (Exception e) {
            log.error("Failed to connect nodes", e);
            return ToolResult.failure(getName(), "連接節點失敗: " + e.getMessage());
        }
    }

    private String findNodeIdByLabel(WorkingFlowDraft draft, String label) {
        return draft.getNodes().stream()
            .filter(n -> n.label().equalsIgnoreCase(label) ||
                        n.label().toLowerCase().contains(label.toLowerCase()))
            .map(WorkingFlowDraft.Node::id)
            .findFirst()
            .orElse(null);
    }
}
