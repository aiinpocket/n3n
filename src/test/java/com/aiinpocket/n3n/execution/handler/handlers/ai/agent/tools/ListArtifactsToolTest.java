package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolExecutionContext;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListArtifactsToolTest extends BaseServiceTest {

    @Mock
    private ArtifactService artifactService;

    private ListArtifactsTool tool;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tool = new ListArtifactsTool(artifactService);
    }

    private ToolExecutionContext contextFor(String userId) {
        return new ToolExecutionContext(userId, null, null, Map.of());
    }

    private Artifact artifact(String filename, String mimeType) {
        return Artifact.builder()
                .id(UUID.randomUUID())
                .ownerId(userId)
                .filename(filename)
                .mimeType(mimeType)
                .sizeBytes(42)
                .storagePath("x")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Basic properties")
    void basicProperties() {
        assertThat(tool.getId()).isEqualTo("list_artifacts");
        assertThat(tool.getCategory()).isEqualTo("platform");
        assertThat(tool.getParametersSchema()).containsKey("properties");
    }

    @Test
    @DisplayName("Fails closed without an authenticated user")
    void failsWithoutUser() throws Exception {
        ToolResult result = tool.execute(Map.of(), contextFor(null)).get();

        assertThat(result.success()).isFalse();
        verify(artifactService, never()).list(any(), any(), any());
    }

    @Test
    @DisplayName("Lists only the current user's artifacts (owner-scoped query)")
    void scopesToOwner() throws Exception {
        when(artifactService.list(eq(userId), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(artifact("a.md", "text/markdown")),
                        PageRequest.of(0, 20), 1));

        ToolResult result = tool.execute(Map.of(), contextFor(userId.toString())).get();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("a.md");
        verify(artifactService).list(eq(userId), isNull(), any(Pageable.class));
    }

    @Test
    @DisplayName("Passes MIME type filter and caps page size at 20")
    void appliesTypeFilterAndLimit() throws Exception {
        when(artifactService.list(eq(userId), eq("text/"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(artifact("b.txt", "text/plain")),
                        PageRequest.of(0, 20), 30));

        ToolResult result = tool.execute(
                Map.of("type", "text/"), contextFor(userId.toString())).get();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("showing newest 20 of 30");
    }

    @Test
    @DisplayName("Empty library returns a friendly message")
    void emptyList() throws Exception {
        when(artifactService.list(eq(userId), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        ToolResult result = tool.execute(Map.of(), contextFor(userId.toString())).get();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("No artifacts found");
    }
}
