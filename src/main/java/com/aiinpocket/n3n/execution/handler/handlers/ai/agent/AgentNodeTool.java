package com.aiinpocket.n3n.execution.handler.handlers.ai.agent;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI Agent 工具介面
 * 定義可被 AI Agent 節點調用的工具
 *
 * 工具用於擴展 AI Agent 的能力，讓 AI 能夠：
 * - 發送 HTTP 請求
 * - 執行程式碼
 * - 搜索資料
 * - 操作資料庫
 * - 等等...
 */
public interface AgentNodeTool {

    /**
     * 工具唯一識別碼
     */
    String getId();

    /**
     * 工具顯示名稱
     */
    String getName();

    /**
     * 工具描述（供 AI 理解用途）
     */
    String getDescription();

    /**
     * 取得工具的 JSON Schema 參數定義
     * 用於 AI 模型的 function calling
     *
     * @return JSON Schema 格式的參數定義
     */
    Map<String, Object> getParametersSchema();

    /**
     * 執行工具
     *
     * @param parameters 工具參數（從 AI 的 function call 解析而來）
     * @param context 執行上下文
     * @return 執行結果
     */
    CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context);

    /**
     * 是否需要用戶確認才能執行
     * 對於危險操作（如刪除、修改）應返回 true
     */
    default boolean requiresConfirmation() {
        return false;
    }

    /**
     * 工具分類
     */
    default String getCategory() {
        return "general";
    }

    /**
     * 工具結果
     */
    record ToolResult(
        boolean success,
        String output,
        Map<String, Object> data,
        String error
    ) {
        public static ToolResult success(String output) {
            return new ToolResult(true, output, null, null);
        }

        public static ToolResult success(String output, Map<String, Object> data) {
            return new ToolResult(true, output, data, null);
        }

        public static ToolResult failure(String error) {
            return new ToolResult(false, null, null, error);
        }
    }

    /**
     * 工具執行上下文
     */
    record ToolExecutionContext(
        String userId,
        String flowId,
        String executionId,
        Map<String, Object> flowVariables
    ) {}
}
