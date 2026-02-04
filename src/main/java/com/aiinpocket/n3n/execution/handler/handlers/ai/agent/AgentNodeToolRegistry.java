package com.aiinpocket.n3n.execution.handler.handlers.ai.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AI Agent 工具註冊表
 * 管理所有可用的 Agent 工具
 *
 * 功能：
 * - 工具註冊/註銷
 * - 按分類查詢工具
 * - 生成 OpenAI/Claude 格式的工具定義
 */
@Component
@Slf4j
public class AgentNodeToolRegistry {

    private final Map<String, AgentNodeTool> tools = new ConcurrentHashMap<>();

    /**
     * 註冊工具
     */
    public void register(AgentNodeTool tool) {
        tools.put(tool.getId(), tool);
        log.info("Registered agent tool: {} ({})", tool.getId(), tool.getName());
    }

    /**
     * 註銷工具
     */
    public void unregister(String toolId) {
        AgentNodeTool removed = tools.remove(toolId);
        if (removed != null) {
            log.info("Unregistered agent tool: {}", toolId);
        }
    }

    /**
     * 取得工具
     */
    public Optional<AgentNodeTool> getTool(String toolId) {
        return Optional.ofNullable(tools.get(toolId));
    }

    /**
     * 取得所有工具
     */
    public Collection<AgentNodeTool> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * 按分類取得工具
     */
    public List<AgentNodeTool> getToolsByCategory(String category) {
        return tools.values().stream()
            .filter(t -> category.equals(t.getCategory()))
            .collect(Collectors.toList());
    }

    /**
     * 取得所有分類
     */
    public Set<String> getCategories() {
        return tools.values().stream()
            .map(AgentNodeTool::getCategory)
            .collect(Collectors.toSet());
    }

    /**
     * 生成 OpenAI 格式的工具定義
     * 用於 function calling
     */
    public List<Map<String, Object>> toOpenAITools(Collection<String> toolIds) {
        return toolIds.stream()
            .map(this::getTool)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(this::toOpenAITool)
            .collect(Collectors.toList());
    }

    /**
     * 生成所有工具的 OpenAI 格式定義
     */
    public List<Map<String, Object>> toOpenAITools() {
        return tools.values().stream()
            .map(this::toOpenAITool)
            .collect(Collectors.toList());
    }

    private Map<String, Object> toOpenAITool(AgentNodeTool tool) {
        return Map.of(
            "type", "function",
            "function", Map.of(
                "name", tool.getId(),
                "description", tool.getDescription(),
                "parameters", tool.getParametersSchema()
            )
        );
    }

    /**
     * 生成 Claude 格式的工具定義
     */
    public List<Map<String, Object>> toClaudeTools(Collection<String> toolIds) {
        return toolIds.stream()
            .map(this::getTool)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .map(this::toClaudeTool)
            .collect(Collectors.toList());
    }

    /**
     * 生成所有工具的 Claude 格式定義
     */
    public List<Map<String, Object>> toClaudeTools() {
        return tools.values().stream()
            .map(this::toClaudeTool)
            .collect(Collectors.toList());
    }

    private Map<String, Object> toClaudeTool(AgentNodeTool tool) {
        return Map.of(
            "name", tool.getId(),
            "description", tool.getDescription(),
            "input_schema", tool.getParametersSchema()
        );
    }

    /**
     * 取得工具簡介列表（供 UI 顯示）
     */
    public List<Map<String, Object>> getToolSummaries() {
        return tools.values().stream()
            .map(t -> Map.<String, Object>of(
                "id", t.getId(),
                "name", t.getName(),
                "description", t.getDescription(),
                "category", t.getCategory(),
                "requiresConfirmation", t.requiresConfirmation()
            ))
            .collect(Collectors.toList());
    }
}
