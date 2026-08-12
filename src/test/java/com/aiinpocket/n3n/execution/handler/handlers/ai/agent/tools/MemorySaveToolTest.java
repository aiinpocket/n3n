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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MemorySaveToolTest extends BaseServiceTest {

    @Mock
    private UserMemoryService userMemoryService;

    private MemorySaveTool tool;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tool = new MemorySaveTool(userMemoryService);
    }

    private ToolExecutionContext contextFor(String userId) {
        return new ToolExecutionContext(userId, null, null, Map.of());
    }

    @Test
    @DisplayName("Basic properties")
    void basicProperties() {
        assertThat(tool.getId()).isEqualTo("memory_save");
        assertThat(tool.getCategory()).isEqualTo("platform");
        assertThat(tool.getDescription()).isNotBlank();
        assertThat(tool.getParametersSchema()).containsKey("properties");
    }

    @Test
    @DisplayName("Saves memory scoped to the context user")
    void execute_savesForContextUser() {
        UserMemory saved = UserMemory.builder()
            .id(UUID.randomUUID())
            .userId(userId)
            .content("prefers Slack")
            .category("preference")
            .source("assistant")
            .build();
        when(userMemoryService.add(eq(userId), eq("prefers Slack"), eq("preference"), eq("assistant")))
            .thenReturn(saved);

        ToolResult result = tool.execute(
            Map.of("content", "prefers Slack", "category", "preference"),
            contextFor(userId.toString())).join();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("prefers Slack");
        verify(userMemoryService).add(eq(userId), eq("prefers Slack"), eq("preference"), eq("assistant"));
    }

    @Test
    @DisplayName("Fails without userId in context")
    void execute_noUserId_fails() {
        ToolResult result = tool.execute(Map.of("content", "x"), contextFor(null)).join();

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("No authenticated user");
        verifyNoInteractions(userMemoryService);
    }

    @Test
    @DisplayName("Fails with malformed userId in context")
    void execute_badUserId_fails() {
        ToolResult result = tool.execute(Map.of("content", "x"), contextFor("not-a-uuid")).join();

        assertThat(result.success()).isFalse();
        verifyNoInteractions(userMemoryService);
    }

    @Test
    @DisplayName("Fails on blank content")
    void execute_blankContent_fails() {
        ToolResult result = tool.execute(Map.of("content", "   "), contextFor(userId.toString())).join();

        assertThat(result.success()).isFalse();
        verify(userMemoryService, never()).add(any(), any(), any(), any());
    }
}
