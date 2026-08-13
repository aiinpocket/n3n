package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.artifact.dto.ArtifactMeta;
import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolExecutionContext;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool.ToolResult;
import com.aiinpocket.n3n.execution.handler.handlers.document.DocumentRenderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GenerateDocumentToolTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private ArtifactService artifactService;
    private GenerateDocumentTool tool;

    @BeforeEach
    void setUp() {
        artifactService = mock(ArtifactService.class);
        tool = new GenerateDocumentTool(new DocumentRenderService(), artifactService);
        when(artifactService.save(eq(USER_ID), any(ArtifactMeta.class), any(byte[].class)))
                .thenAnswer(inv -> {
                    ArtifactMeta meta = inv.getArgument(1);
                    byte[] data = inv.getArgument(2);
                    return Artifact.builder()
                            .id(UUID.randomUUID())
                            .ownerId(USER_ID)
                            .filename(meta.getFilename())
                            .mimeType(meta.getMimeType())
                            .sizeBytes(data.length)
                            .build();
                });
    }

    @Test
    void toolIdentity() {
        assertThat(tool.getId()).isEqualTo("generate_document");
        assertThat(tool.getParametersSchema()).containsKey("properties");
        assertThat(tool.getCategory()).isEqualTo("platform");
    }

    @Test
    @DisplayName("fails closed when there is no authenticated user")
    void failsClosedWithoutUser() {
        ToolResult noContext = tool.execute(
                Map.of("format", "docx", "markdown", "# Hi"), null).join();
        ToolResult blankUser = tool.execute(
                Map.of("format", "docx", "markdown", "# Hi"),
                new ToolExecutionContext("", "f", "e", Map.of())).join();
        ToolResult badUser = tool.execute(
                Map.of("format", "docx", "markdown", "# Hi"),
                new ToolExecutionContext("not-a-uuid", "f", "e", Map.of())).join();

        assertThat(noContext.success()).isFalse();
        assertThat(blankUser.success()).isFalse();
        assertThat(badUser.success()).isFalse();
        verify(artifactService, never()).save(any(), any(), any(byte[].class));
    }

    @Test
    @DisplayName("rejects unknown formats")
    void rejectsInvalidFormat() {
        ToolResult result = tool.execute(
                Map.of("format", "pdf", "markdown", "# Hi"), context()).join();

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("pptx").contains("docx");
        verify(artifactService, never()).save(any(), any(), any(byte[].class));
    }

    @Test
    @DisplayName("requires markdown")
    void requiresMarkdown() {
        ToolResult result = tool.execute(Map.of("format", "docx"), context()).join();

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("markdown");
    }

    @Test
    @DisplayName("generates a pptx artifact for the calling user")
    void generatesPptx() {
        ToolResult result = tool.execute(Map.of(
                "format", "pptx",
                "title", "Deck",
                "markdown", "# Slide A\n- point one\n\n# Slide B\n- point two"
        ), context()).join();

        assertThat(result.success()).isTrue();
        assertThat(result.data()).containsKeys("artifactId", "downloadUrl", "filename");
        assertThat(result.data().get("filename")).isEqualTo("Deck.pptx");

        ArgumentCaptor<ArtifactMeta> captor = ArgumentCaptor.forClass(ArtifactMeta.class);
        verify(artifactService).save(eq(USER_ID), captor.capture(), any(byte[].class));
        assertThat(captor.getValue().getMimeType()).contains("presentationml");
    }

    @Test
    @DisplayName("generates a docx artifact with the requested filename")
    void generatesDocx() {
        ToolResult result = tool.execute(Map.of(
                "format", "docx",
                "markdown", "# Report\nAll good.",
                "filename", "status"
        ), context()).join();

        assertThat(result.success()).isTrue();
        assertThat(result.data().get("filename")).isEqualTo("status.docx");

        ArgumentCaptor<ArtifactMeta> captor = ArgumentCaptor.forClass(ArtifactMeta.class);
        verify(artifactService).save(eq(USER_ID), captor.capture(), any(byte[].class));
        assertThat(captor.getValue().getMimeType()).contains("wordprocessingml");
    }

    private ToolExecutionContext context() {
        return new ToolExecutionContext(
                USER_ID.toString(), UUID.randomUUID().toString(), UUID.randomUUID().toString(), Map.of());
    }
}
