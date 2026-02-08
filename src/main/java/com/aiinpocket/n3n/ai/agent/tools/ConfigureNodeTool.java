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
        return "Configure node parameters such as URL, authentication, SQL queries, and other settings.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "nodeId", Map.of(
                    "type", "string",
                    "description", "ID of the node to configure"
                ),
                "nodeLabel", Map.of(
                    "type", "string",
                    "description", "Name of the node to configure (if ID is unknown)"
                ),
                "config", Map.of(
                    "type", "object",
                    "description", "Configuration parameters to set"
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
                return ToolResult.failure(getName(), "Configuration parameters are required");
            }

            WorkingFlowDraft draft = context.getFlowDraft();
            if (draft == null || !draft.hasContent()) {
                return ToolResult.failure(getName(), "No flow draft available");
            }

            // 解析節點 ID
            String targetId = nodeId;
            if (targetId == null && nodeLabel != null) {
                targetId = findNodeIdByLabel(draft, nodeLabel);
                if (targetId == null) {
                    return ToolResult.failure(getName(),
                        "Node with label '" + nodeLabel + "' not found");
                }
            }

            if (targetId == null) {
                return ToolResult.failure(getName(), "Either nodeId or nodeLabel is required");
            }

            // 驗證節點存在
            Optional<WorkingFlowDraft.Node> nodeOpt = draft.getNode(targetId);
            if (nodeOpt.isEmpty()) {
                return ToolResult.failure(getName(),
                    "Node with ID '" + targetId + "' not found");
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
            return ToolResult.failure(getName(), "Failed to configure node");
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
