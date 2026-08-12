package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolExecutionContext;

import java.util.UUID;

/**
 * Shared security helpers for agent tools: user id parsing and
 * safe UUID handling. Keeps ownership-scoping logic consistent.
 */
final class ToolSecurity {

    private ToolSecurity() {
    }

    /**
     * Parse the authenticated user id from the tool execution context.
     * Returns null when missing or malformed — callers must fail closed.
     */
    static UUID parseUserId(ToolExecutionContext context) {
        return parseUuid(context != null ? context.userId() : null);
    }

    static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
