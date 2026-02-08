package com.aiinpocket.n3n.execution.handler.handlers.ai.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AI Agent Tool Registry.
 * Manages all available Agent tools.
 *
 * Features:
 * - Tool registration/unregistration
 * - Query tools by category
 * - Generate tool definitions in OpenAI/Claude format
 */
@Component
@Slf4j
public class AgentNodeToolRegistry {

    private final Map<String, AgentNodeTool> tools = new ConcurrentHashMap<>();

    /**
     * Register a tool
     */
    public void register(AgentNodeTool tool) {
        tools.put(tool.getId(), tool);
        log.info("Registered agent tool: {} ({})", tool.getId(), tool.getName());
    }

    /**
     * Unregister a tool
     */
    public void unregister(String toolId) {
        AgentNodeTool removed = tools.remove(toolId);
        if (removed != null) {
            log.info("Unregistered agent tool: {}", toolId);
        }
    }

    /**
     * Get a tool
     */
    public Optional<AgentNodeTool> getTool(String toolId) {
        return Optional.ofNullable(tools.get(toolId));
    }

    /**
     * Get all tools
     */
    public Collection<AgentNodeTool> getAllTools() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * Get tools by category
     */
    public List<AgentNodeTool> getToolsByCategory(String category) {
        return tools.values().stream()
            .filter(t -> category.equals(t.getCategory()))
            .collect(Collectors.toList());
    }

    /**
     * Get all categories
     */
    public Set<String> getCategories() {
        return tools.values().stream()
            .map(AgentNodeTool::getCategory)
            .collect(Collectors.toSet());
    }

    /**
     * Generate tool definitions in OpenAI format.
     * Used for function calling.
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
     * Generate OpenAI format definitions for all tools
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
     * Generate tool definitions in Claude format
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
     * Generate Claude format definitions for all tools
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
     * Get tool summary list (for UI display)
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
