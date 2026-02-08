package com.aiinpocket.n3n.execution.handler.handlers.ai.agent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AI Agent Tool Interface.
 * Defines tools that can be invoked by AI Agent nodes.
 *
 * Tools extend the AI Agent's capabilities, enabling:
 * - Sending HTTP requests
 * - Executing code
 * - Searching data
 * - Database operations
 * - And more...
 */
public interface AgentNodeTool {

    /**
     * Unique tool identifier
     */
    String getId();

    /**
     * Tool display name
     */
    String getName();

    /**
     * Tool description (for AI to understand its purpose)
     */
    String getDescription();

    /**
     * Get the tool's JSON Schema parameter definition.
     * Used for AI model function calling.
     *
     * @return parameter definition in JSON Schema format
     */
    Map<String, Object> getParametersSchema();

    /**
     * Execute the tool
     *
     * @param parameters tool parameters (parsed from AI's function call)
     * @param context execution context
     * @return execution result
     */
    CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context);

    /**
     * Whether user confirmation is required before execution.
     * Should return true for dangerous operations (e.g., delete, modify).
     */
    default boolean requiresConfirmation() {
        return false;
    }

    /**
     * Tool execution timeout (in seconds)
     */
    default int getTimeoutSeconds() {
        return 30;
    }

    /**
     * Get the credential types required by this tool.
     * Used to verify whether sufficient permissions exist for execution.
     */
    default List<String> getRequiredCredentials() {
        return List.of();
    }

    /**
     * Tool category
     */
    default String getCategory() {
        return "general";
    }

    /**
     * Tool result
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
     * Tool execution context
     */
    record ToolExecutionContext(
        String userId,
        String flowId,
        String executionId,
        Map<String, Object> flowVariables
    ) {}
}
