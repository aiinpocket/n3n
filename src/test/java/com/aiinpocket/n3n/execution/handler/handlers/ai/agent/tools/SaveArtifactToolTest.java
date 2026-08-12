package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.artifact.dto.ArtifactMeta;
import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolExecutionContext;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaveArtifactToolTest extends BaseServiceTest {

    @Mock
    private ArtifactService artifactService;

    private SaveArtifactTool tool;

    private final UUID userId = UUID.randomUUID();
    private final UUID artifactId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tool = new SaveArtifactTool(artifactService);
    }

    private ToolExecutionContext contextFor(String userId) {
        return new ToolExecutionContext(userId, null, null, Map.of());
    }

    private Artifact savedArtifact() {
        return Artifact.builder()
                .id(artifactId)
                .ownerId(userId)
                .filename("report.md")
                .mimeType("text/markdown")
                .sizeBytes(11)
                .storagePath("x")
                .build();
    }

    @Test
    @DisplayName("Basic properties")
    void basicProperties() {
        assertThat(tool.getId()).isEqualTo("save_artifact");
        assertThat(tool.getCategory()).isEqualTo("platform");
        assertThat(tool.getParametersSchema()).containsKey("required");
    }

    @Test
    @DisplayName("Fails closed without an authenticated user")
    void failsWithoutUser() throws Exception {
        ToolResult result = tool.execute(
                Map.of("filename", "a.md", "content", "hi"), contextFor(null)).get();

        assertThat(result.success()).isFalse();
        verify(artifactService, never()).save(any(), any(), any(byte[].class));
    }

    @Test
    @DisplayName("Saves under the requesting user with default content type")
    void savesForOwner() throws Exception {
        when(artifactService.save(eq(userId), any(), any(byte[].class)))
                .thenReturn(savedArtifact());

        ToolResult result = tool.execute(
                Map.of("filename", "report.md", "content", "hello world"),
                contextFor(userId.toString())).get();

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains(artifactId.toString());
        assertThat(result.output()).contains("/api/artifacts/" + artifactId + "/download");

        ArgumentCaptor<ArtifactMeta> metaCaptor = ArgumentCaptor.forClass(ArtifactMeta.class);
        verify(artifactService).save(eq(userId), metaCaptor.capture(), any(byte[].class));
        assertThat(metaCaptor.getValue().getMimeType()).isEqualTo("text/markdown");
        assertThat(metaCaptor.getValue().getSourceNodeType()).isEqualTo("aiAgent");
    }

    @Test
    @DisplayName("Missing filename or content fails with clear error")
    void validatesRequiredParams() throws Exception {
        ToolResult noFilename = tool.execute(
                Map.of("content", "hi"), contextFor(userId.toString())).get();
        ToolResult noContent = tool.execute(
                Map.of("filename", "a.md"), contextFor(userId.toString())).get();

        assertThat(noFilename.success()).isFalse();
        assertThat(noFilename.error()).contains("filename");
        assertThat(noContent.success()).isFalse();
        assertThat(noContent.error()).contains("content");
        verify(artifactService, never()).save(any(), any(), any(byte[].class));
    }

    @Test
    @DisplayName("Max-file-size violation surfaces as a tool error")
    void surfacesSizeLimit() throws Exception {
        when(artifactService.save(eq(userId), any(), any(byte[].class)))
                .thenThrow(new IllegalArgumentException("Artifact exceeds max file size of 100 MB"));

        ToolResult result = tool.execute(
                Map.of("filename", "big.md", "content", "x"),
                contextFor(userId.toString())).get();

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("max file size");
    }
}
