package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolExecutionContext;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.core.io.ByteArrayResource;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadArtifactToolTest extends BaseServiceTest {

    @Mock
    private ArtifactService artifactService;

    private ReadArtifactTool tool;

    private final UUID userId = UUID.randomUUID();
    private final UUID artifactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tool = new ReadArtifactTool(artifactService);
    }

    private ToolExecutionContext contextFor(String userId) {
        return new ToolExecutionContext(userId, null, null, Map.of());
    }

    private Artifact artifact(String mimeType) {
        return Artifact.builder()
                .id(artifactId)
                .ownerId(userId)
                .filename("doc.md")
                .mimeType(mimeType)
                .sizeBytes(100)
                .storagePath("x")
                .build();
    }

    @Test
    @DisplayName("Basic properties")
    void basicProperties() {
        assertThat(tool.getId()).isEqualTo("read_artifact");
        assertThat(tool.getCategory()).isEqualTo("platform");
        assertThat(tool.getParametersSchema()).containsKey("required");
    }

    @Test
    @DisplayName("Non-owner gets generic not-found (no existence leak)")
    void noExistenceLeak() throws Exception {
        when(artifactService.getOwned(artifactId, userId))
                .thenThrow(new ResourceNotFoundException("Artifact not found: " + artifactId));

        ToolResult result = tool.execute(
                Map.of("artifactId", artifactId.toString()),
                contextFor(userId.toString())).get();

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("Artifact not found");
        verify(artifactService, never()).openResource(any());
    }

    @Test
    @DisplayName("Fails closed without an authenticated user")
    void failsWithoutUser() throws Exception {
        ToolResult result = tool.execute(
                Map.of("artifactId", artifactId.toString()), contextFor(null)).get();

        assertThat(result.success()).isFalse();
        verify(artifactService, never()).getOwned(any(), any());
    }

    @Test
    @DisplayName("Reads text artifact content")
    void readsTextContent() throws Exception {
        when(artifactService.getOwned(artifactId, userId)).thenReturn(artifact("text/markdown"));
        when(artifactService.openResource(any()))
                .thenReturn(new ByteArrayResource("hello world".getBytes(StandardCharsets.UTF_8)));

        ToolResult result = tool.execute(
                Map.of("artifactId", artifactId.toString()),
                contextFor(userId.toString())).get();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("hello world");
    }

    @Test
    @DisplayName("Truncates content longer than 8000 characters")
    void truncatesLongContent() throws Exception {
        String longContent = "x".repeat(10000);
        when(artifactService.getOwned(artifactId, userId)).thenReturn(artifact("text/plain"));
        when(artifactService.openResource(any()))
                .thenReturn(new ByteArrayResource(longContent.getBytes(StandardCharsets.UTF_8)));

        ToolResult result = tool.execute(
                Map.of("artifactId", artifactId.toString()),
                contextFor(userId.toString())).get();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("truncated at 8000 characters");
        // metadata header + 8000 chars + truncation note — never the full 10000
        assertThat(result.output().length()).isLessThan(8500);
    }

    @Test
    @DisplayName("Binary artifact returns metadata only, never content")
    void binaryReturnsMetadataOnly() throws Exception {
        when(artifactService.getOwned(artifactId, userId)).thenReturn(artifact("image/png"));

        ToolResult result = tool.execute(
                Map.of("artifactId", artifactId.toString()),
                contextFor(userId.toString())).get();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("binary");
        assertThat(result.output()).contains("doc.md");
        verify(artifactService, never()).openResource(any());
    }
}
