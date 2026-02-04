package com.aiinpocket.n3n.ai.agent.tools;

import com.aiinpocket.n3n.ai.agent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 移除節點工具
 * 從流程草稿中移除指定節點
 */
@Slf4j
@Component
public class RemoveNodeTool implements AgentTool {

    @Override
    public String getName() {
        return "remove_node";
    }

    @Override
    public String getDescription() {
        return "從流程中移除指定的節點。會同時移除相關的連線。";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "nodeId", Map.of(
                    "type", "string",
                    "description", "要移除的節點 ID"
                ),
                "nodeLabel", Map.of(
                    "type", "string",
                    "description", "要移除的節點名稱（如果不知道 ID）"
                )
            ),
            "required", List.of()
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, AgentContext context) {
        long startTime = System.currentTimeMillis();
        String nodeId = (String) parameters.get("nodeId");
        String nodeLabel = (String) parameters.get("nodeLabel");

        log.debug("Removing node: id={}, label={}", nodeId, nodeLabel);

        try {
            WorkingFlowDraft draft = context.getFlowDraft();
            if (draft == null || !draft.hasContent()) {
                return ToolResult.failure(getName(), "沒有可操作的流程草稿");
            }

            // 如果提供的是標籤，找到對應的 ID
            String targetId = nodeId;
            if (targetId == null && nodeLabel != null) {
                targetId = findNodeIdByLabel(draft, nodeLabel);
                if (targetId == null) {
                    return ToolResult.failure(getName(),
                        "找不到名稱為 '" + nodeLabel + "' 的節點");
                }
            }

            if (targetId == null) {
                return ToolResult.failure(getName(), "必須提供 nodeId 或 nodeLabel");
            }

            // 記錄移除前的狀態
            Optional<WorkingFlowDraft.Node> nodeOpt = draft.getNode(targetId);
            Map<String, Object> removedNode = nodeOpt
                .map(n -> Map.<String, Object>of(
                    "id", n.id(),
                    "type", n.type(),
                    "label", n.label()
                ))
                .orElse(null);

            // 執行移除
            boolean removed = draft.removeNode(targetId);
            if (!removed) {
                return ToolResult.failure(getName(),
                    "找不到 ID 為 '" + targetId + "' 的節點");
            }

            long duration = System.currentTimeMillis() - startTime;
            return ToolResult.builder()
                .toolName(getName())
                .success(true)
                .data(Map.of(
                    "removedNodeId", targetId,
                    "removedNode", removedNode != null ? removedNode : Map.of(),
                    "flowDraft", draft.toDefinition()
                ))
                .durationMs(duration)
                .build();

        } catch (Exception e) {
            log.error("Failed to remove node", e);
            return ToolResult.failure(getName(), "移除節點失敗: " + e.getMessage());
        }
    }

    private String findNodeIdByLabel(WorkingFlowDraft draft, String label) {
        return draft.getNodes().stream()
            .filter(n -> n.label().equalsIgnoreCase(label) ||
                        n.label().contains(label))
            .map(WorkingFlowDraft.Node::id)
            .findFirst()
            .orElse(null);
    }

    @Override
    public boolean requiresConfirmation() {
        return true; // 移除需要確認
    }
}
