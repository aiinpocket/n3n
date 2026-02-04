package com.aiinpocket.n3n.ai.agent.tools;

import com.aiinpocket.n3n.ai.agent.*;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 新增節點工具
 * 在流程草稿中新增節點
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AddNodeTool implements AgentTool {

    private final NodeHandlerRegistry nodeHandlerRegistry;

    @Override
    public String getName() {
        return "add_node";
    }

    @Override
    public String getDescription() {
        return "在流程中新增一個節點。需指定節點類型和標籤。";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "nodeType", Map.of(
                    "type", "string",
                    "description", "節點類型（例如：httpRequest、sendEmail、code）"
                ),
                "label", Map.of(
                    "type", "string",
                    "description", "節點顯示名稱"
                ),
                "config", Map.of(
                    "type", "object",
                    "description", "節點配置參數"
                ),
                "connectAfter", Map.of(
                    "type", "string",
                    "description", "連接到此節點後方的節點 ID（可選）"
                )
            ),
            "required", List.of("nodeType", "label")
        );
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, AgentContext context) {
        long startTime = System.currentTimeMillis();
        String nodeType = (String) parameters.get("nodeType");
        String label = (String) parameters.get("label");
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) parameters.get("config");
        String connectAfter = (String) parameters.get("connectAfter");

        log.debug("Adding node: type={}, label={}", nodeType, label);

        try {
            // 驗證節點類型是否存在
            if (!nodeHandlerRegistry.hasHandler(nodeType)) {
                // 嘗試模糊匹配
                String suggestion = findSimilarType(nodeType);
                String errorMsg = "未知的節點類型: " + nodeType;
                if (suggestion != null) {
                    errorMsg += "，您是否指的是 " + suggestion + "？";
                }
                return ToolResult.failure(getName(), errorMsg);
            }

            // 確保有流程草稿
            WorkingFlowDraft draft = context.getFlowDraft();
            if (draft == null) {
                draft = new WorkingFlowDraft();
                context.setFlowDraft(draft);
            }

            // 新增節點
            String nodeId = draft.addNode(nodeType, label, config != null ? config : new HashMap<>());
            log.debug("Added node with ID: {}", nodeId);

            // 如果指定了連接目標
            if (connectAfter != null && !connectAfter.isBlank()) {
                draft.connectNodes(connectAfter, nodeId);
                log.debug("Connected {} -> {}", connectAfter, nodeId);
            }

            // 記錄變更
            Map<String, Object> addedNode = Map.of(
                "id", nodeId,
                "type", nodeType,
                "label", label,
                "config", config != null ? config : Map.of()
            );

            long duration = System.currentTimeMillis() - startTime;
            return ToolResult.builder()
                .toolName(getName())
                .success(true)
                .data(Map.of(
                    "nodeId", nodeId,
                    "node", addedNode,
                    "flowDraft", draft.toDefinition()
                ))
                .durationMs(duration)
                .build();

        } catch (Exception e) {
            log.error("Failed to add node", e);
            return ToolResult.failure(getName(), "新增節點失敗: " + e.getMessage());
        }
    }

    private String findSimilarType(String unknownType) {
        if (unknownType == null) return null;

        String lower = unknownType.toLowerCase();
        List<String> types = nodeHandlerRegistry.getRegisteredTypes();

        // 嘗試找相似的
        for (String type : types) {
            if (type.toLowerCase().contains(lower) ||
                lower.contains(type.toLowerCase())) {
                return type;
            }
        }
        return null;
    }

    @Override
    public boolean requiresConfirmation() {
        return false; // 新增節點不需要確認，刪除才需要
    }
}
