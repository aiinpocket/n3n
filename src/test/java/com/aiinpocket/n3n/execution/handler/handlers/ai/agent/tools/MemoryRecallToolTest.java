package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.ai.usermemory.entity.UserMemory;
import com.aiinpocket.n3n.ai.usermemory.service.UserMemoryService;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolExecutionContext;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MemoryRecallToolTest extends BaseServiceTest {

    @Mock
    private UserMemoryService userMemoryService;

    private MemoryRecallTool tool;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tool = new MemoryRecallTool(userMemoryService);
    }

    private ToolExecutionContext contextFor(String userId) {
        return new ToolExecutionContext(userId, null, null, Map.of());
    }

    private UserMemory memory(String content) {
        return UserMemory.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .content(content)
            .category("general")
            .source("assistant")
            .build();
    }

    @Test
    @DisplayName("Basic properties")
    void basicProperties() {
        assertThat(tool.getId()).isEqualTo("memory_recall");
        assertThat(tool.getCategory()).isEqualTo("platform");
        assertThat(tool.getDescription()).isNotBlank();
    }

    @Test
    @DisplayName("Returns memories scoped to the context user")
    void execute_returnsUserMemories() {
        when(userMemoryService.list(userId)).thenReturn(List.of(
            memory("prefers Slack"),
            memory("uses PostgreSQL")
        ));

        ToolResult result = tool.execute(Map.of(), contextFor(userId.toString())).join();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("prefers Slack").contains("uses PostgreSQL");
        verify(userMemoryService).list(userId);
    }

    @Test
    @DisplayName("Filters by contains match when query given")
    void execute_withQuery_filters() {
        when(userMemoryService.list(userId)).thenReturn(List.of(
            memory("prefers Slack notifications"),
            memory("uses PostgreSQL")
        ));

        ToolResult result = tool.execute(Map.of("query", "slack"), contextFor(userId.toString())).join();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("Slack");
        assertThat(result.output()).doesNotContain("PostgreSQL");
    }

    @Test
    @DisplayName("Caps results at 20 entries")
    void execute_capsAt20() {
        List<UserMemory> many = IntStream.range(0, 30)
            .mapToObj(i -> memory("memory number " + i))
            .toList();
        when(userMemoryService.list(userId)).thenReturn(many);

        ToolResult result = tool.execute(Map.of(), contextFor(userId.toString())).join();

        assertThat(result.success()).isTrue();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> memories = (List<Map<String, Object>>) result.data().get("memories");
        assertThat(memories).hasSize(20);
    }

    @Test
    @DisplayName("Fails without userId in context")
    void execute_noUserId_fails() {
        ToolResult result = tool.execute(Map.of(), contextFor(null)).join();

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("No authenticated user");
        verifyNoInteractions(userMemoryService);
    }

    @Test
    @DisplayName("Returns success with empty list when no memories")
    void execute_noMemories_returnsEmpty() {
        when(userMemoryService.list(userId)).thenReturn(List.of());

        ToolResult result = tool.execute(Map.of(), contextFor(userId.toString())).join();

        assertThat(result.success()).isTrue();
        assertThat(result.data().get("memories")).isEqualTo(List.of());
    }
}
