package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.ai.usermemory.entity.UserMemory;
import com.aiinpocket.n3n.ai.usermemory.service.UserMemoryService;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Agent tool that saves a long-term memory about the current user.
 * Memories persist across conversations and are injected into future AI context.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MemorySaveTool implements AgentNodeTool {

    private final UserMemoryService userMemoryService;

    @Override
    public String getId() {
        return "memory_save";
    }

    @Override
    public String getName() {
        return "Save Memory";
    }

    @Override
    public String getDescription() {
        return """
                Saves a long-term memory about the current user, persisted across conversations.

                Use this when the user shares a lasting preference, fact, or habit worth
                remembering (e.g. "I prefer Slack notifications", "my team uses PostgreSQL").
                Keep each memory short and self-contained.

                Parameters:
                - content: The memory text to save (required)
                - category: One of preference | fact | project | style | general (default: general)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "content", Map.of(
                                "type", "string",
                                "description", "The memory text to save (short, self-contained)"
                        ),
                        "category", Map.of(
                                "type", "string",
                                "enum", List.of("preference", "fact", "project", "style", "general"),
                                "description", "Memory category"
                        )
                ),
                "required", List.of("content")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                UUID userId = parseUserId(context);
                if (userId == null) {
                    return ToolResult.failure("No authenticated user in execution context");
                }

                String content = parameters.get("content") instanceof String s ? s.trim() : "";
                if (content.isEmpty()) {
                    return ToolResult.failure("Memory content must not be blank");
                }

                String category = parameters.get("category") instanceof String c ? c : "general";

                UserMemory saved = userMemoryService.add(userId, content, category, "assistant");

                return ToolResult.success(
                        "Memory saved: [" + saved.getCategory() + "] " + saved.getContent(),
                        Map.of(
                                "id", saved.getId().toString(),
                                "category", saved.getCategory()
                        ));

            } catch (IllegalArgumentException e) {
                return ToolResult.failure(e.getMessage());
            } catch (Exception e) {
                log.error("memory_save tool failed", e);
                return ToolResult.failure("Failed to save memory");
            }
        });
    }

    private UUID parseUserId(ToolExecutionContext context) {
        if (context == null || context.userId() == null) {
            return null;
        }
        try {
            return UUID.fromString(context.userId());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String getCategory() {
        return "platform";
    }
}
