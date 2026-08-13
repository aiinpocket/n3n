package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.ai.rag.RagService;
import com.aiinpocket.n3n.ai.rag.document.Document;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolExecutionContext;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VectorSearchToolTest {

    @Mock
    private RagService ragService;

    private VectorSearchTool tool;

    @BeforeEach
    void setUp() {
        tool = new VectorSearchTool(ragService);
    }

    private ToolExecutionContext ctx(String userId) {
        return new ToolExecutionContext(userId, "flow-1", "exec-1", Map.of());
    }

    @Test
    @DisplayName("Missing userId -> fail closed, RagService never queried")
    void missingUserId_failsClosed() throws Exception {
        ToolResult result = tool.execute(Map.of("query", "hello"), ctx(null))
                .get();

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("User context");
        verifyNoInteractions(ragService);
    }

    @Test
    @DisplayName("Malformed userId -> fail closed")
    void malformedUserId_failsClosed() throws Exception {
        ToolResult result = tool.execute(Map.of("query", "hello"), ctx("not-a-uuid"))
                .get();

        assertThat(result.success()).isFalse();
        verifyNoInteractions(ragService);
    }

    @Test
    @DisplayName("Valid userId is threaded into RagService as the tenant key")
    void validUserId_passedToRagService() throws Exception {
        UUID userId = UUID.randomUUID();
        when(ragService.search(anyString(), anyInt(), any(), anyString()))
                .thenReturn(List.of(Document.of("doc", Map.of())));

        tool.execute(Map.of("query", "hello", "store_name", "kb"), ctx(userId.toString()))
                .get();

        verify(ragService).search(eq("hello"), anyInt(), eq("kb"), eq(userId.toString()));
    }
}
