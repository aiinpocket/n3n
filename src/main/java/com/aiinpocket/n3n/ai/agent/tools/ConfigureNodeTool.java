package com.aiinpocket.n3n.ai.agent.tools;

import com.aiinpocket.n3n.ai.agent.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 配置節點工具
 * 修改節點的配置參數
 */
@Slf4j
@Component
public class ConfigureNodeTool implements AgentTool {

    @Override
    public String getName() {
        return "configure_node";
    }

    @Override
    public String getDescription() {
        return "配置節點的參數。可設定 URL、認證、SQL 語句等節點特定設定。";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "nodeId", Map.of(
                    "type", "string",
                    "description", "要配置的節點 ID"
                ),
                "nodeLabel", Map.of(
                    "type", "string",
                    "description", "要配置的節點名稱（如果不知道 ID）"
                ),
                "config", Map.of(
                    "type", "object",
                    "description", "要設定的配置參數"
                )
            ),
            "required", List.of("config")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, AgentContext context) {
        long startTime = System.currentTimeMillis();
        String nodeId = (String) parameters.get("nodeId");
        String nodeLabel = (String) parameters.get("nodeLabel");
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) parameters.get("config");

        log.debug("Configuring node: id={}, label={}, config={}",
            nodeId, nodeLabel, config);

        try {
            if (config == null || config.isEmpty()) {
                return ToolResult.failure(getName(), "必須提供配置參數");
            }

            WorkingFlowDraft draft = context.getFlowDraft();
            if (draft == null || !draft.hasContent()) {
                return ToolResult.failure(getName(), "沒有可操作的流程草稿");
            }

            // 解析節點 ID
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

            // 驗證節點存在
            Optional<WorkingFlowDraft.Node> nodeOpt = draft.getNode(targetId);
            if (nodeOpt.isEmpty()) {
                return ToolResult.failure(getName(),
                    "找不到 ID 為 '" + targetId + "' 的節點");
            }

            WorkingFlowDraft.Node node = nodeOpt.get();

            // 記錄配置前狀態
            Map<String, Object> beforeConfig = new HashMap<>(node.config());

            // 執行配置
            draft.configureNode(targetId, config);

            // 取得配置後狀態
            Map<String, Object> afterConfig = draft.getNode(targetId)
                .map(WorkingFlowDraft.Node::config)
                .orElse(Map.of());

            long duration = System.currentTimeMillis() - startTime;
            return ToolResult.builder()
                .toolName(getName())
                .success(true)
                .data(Map.of(
                    "nodeId", targetId,
                    "nodeLabel", node.label(),
                    "beforeConfig", beforeConfig,
                    "afterConfig", afterConfig,
                    "appliedConfig", config,
                    "flowDraft", draft.toDefinition()
                ))
                .durationMs(duration)
                .build();

        } catch (Exception e) {
            log.error("Failed to configure node", e);
            return ToolResult.failure(getName(), "配置節點失敗: " + e.getMessage());
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
