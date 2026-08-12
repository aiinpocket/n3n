package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.ai.usermemory.entity.UserMemory;
import com.aiinpocket.n3n.ai.usermemory.service.UserMemoryService;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Agent tool that recalls the current user's long-term memories.
 * Optionally filters by a simple case-insensitive contains match.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MemoryRecallTool implements AgentNodeTool {

    private static final int MAX_RESULTS = 20;

    private final UserMemoryService userMemoryService;

    @Override
    public String getId() {
        return "memory_recall";
    }

    @Override
    public String getName() {
        return "Recall Memory";
    }

    @Override
    public String getDescription() {
        return """
                Recalls the current user's long-term memories saved in previous interactions.

                Returns up to 20 memories (newest first), each with its category and content.
                Use this to personalize answers with the user's known preferences and context.

                Parameters:
                - query: Optional filter; only memories containing this text are returned (case-insensitive)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "description", "Optional filter: only return memories containing this text (case-insensitive)"
                        )
                ),
                "required", List.of()
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

                String query = parameters.get("query") instanceof String s
                        ? s.trim().toLowerCase(Locale.ROOT) : "";

                List<UserMemory> memories = userMemoryService.list(userId).stream()
                        .filter(m -> query.isEmpty()
                                || (m.getContent() != null
                                    && m.getContent().toLowerCase(Locale.ROOT).contains(query)))
                        .limit(MAX_RESULTS)
                        .toList();

                if (memories.isEmpty()) {
                    return ToolResult.success(
                            query.isEmpty() ? "No memories stored." : "No memories found matching: " + query,
                            Map.of("memories", List.of()));
                }

                List<Map<String, Object>> memoryList = new ArrayList<>();
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("Found %d memorie(s):%n", memories.size()));

                for (UserMemory memory : memories) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("id", memory.getId().toString());
                    entry.put("content", memory.getContent());
                    entry.put("category", memory.getCategory());
                    entry.put("source", memory.getSource());
                    memoryList.add(entry);

                    sb.append(String.format("- [%s] %s%n", memory.getCategory(), memory.getContent()));
                }

                return ToolResult.success(sb.toString(), Map.of("memories", memoryList));

            } catch (Exception e) {
                log.error("memory_recall tool failed", e);
                return ToolResult.failure("Failed to recall memories");
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
