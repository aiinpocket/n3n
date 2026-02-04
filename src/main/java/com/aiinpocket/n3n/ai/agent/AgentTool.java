package com.aiinpocket.n3n.ai.agent;

import java.util.Map;

/**
 * Agent 工具介面
 * 工具是 Agent 可調用的具體操作
 */
public interface AgentTool {

    /**
     * 工具名稱（用於 AI function calling）
     */
    String getName();

    /**
     * 工具描述（用於 AI 理解何時使用）
     */
    String getDescription();

    /**
     * 輸入參數 JSON Schema
     */
    Map<String, Object> getParameterSchema();

    /**
     * 執行工具
     */
    ToolResult execute(Map<String, Object> parameters, AgentContext context);

    /**
     * 是否需要確認（危險操作）
     */
    default boolean requiresConfirmation() {
        return false;
    }
}
