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
        return "Connect two nodes. Creates a data flow from source node to target node.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "sourceId", Map.of(
                    "type", "string",
                    "description", "Source node ID"
                ),
                "targetId", Map.of(
                    "type", "string",
                    "description", "Target node ID"
                ),
                "sourceLabel", Map.of(
                    "type", "string",
                    "description", "Source node name (if ID is unknown)"
                ),
                "targetLabel", Map.of(
                    "type", "string",
                    "description", "Target node name (if ID is unknown)"
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
                return ToolResult.failure(getName(), "No flow draft available");
            }

            String resolvedSourceId = sourceId;
            if (resolvedSourceId == null && sourceLabel != null) {
                resolvedSourceId = findNodeIdByLabel(draft, sourceLabel);
                if (resolvedSourceId == null) {
                    return ToolResult.failure(getName(),
                        "Source node with label '" + sourceLabel + "' not found");
                }
            }

            String resolvedTargetId = targetId;
            if (resolvedTargetId == null && targetLabel != null) {
                resolvedTargetId = findNodeIdByLabel(draft, targetLabel);
                if (resolvedTargetId == null) {
                    return ToolResult.failure(getName(),
                        "Target node with label '" + targetLabel + "' not found");
                }
            }

            if (resolvedSourceId == null || resolvedTargetId == null) {
                return ToolResult.failure(getName(),
                    "Both source and target node ID or label are required");
            }

            if (draft.getNode(resolvedSourceId).isEmpty()) {
                return ToolResult.failure(getName(),
                    "Source node '" + resolvedSourceId + "' does not exist");
            }
            if (draft.getNode(resolvedTargetId).isEmpty()) {
                return ToolResult.failure(getName(),
                    "Target node '" + resolvedTargetId + "' does not exist");
            }

            if (resolvedSourceId.equals(resolvedTargetId)) {
                return ToolResult.failure(getName(), "Cannot connect a node to itself");
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
            return ToolResult.failure(getName(), "Failed to connect nodes");
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
