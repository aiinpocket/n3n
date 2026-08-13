package com.aiinpocket.n3n.execution.handler.handlers.document;

import com.aiinpocket.n3n.artifact.dto.ArtifactMeta;
import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PptxGenerateNodeHandlerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private ArtifactService artifactService;
    private PptxGenerateNodeHandler handler;

    @BeforeEach
    void setUp() {
        artifactService = mock(ArtifactService.class);
        handler = new PptxGenerateNodeHandler(
                new DocumentRenderService(), artifactService, new ObjectMapper());
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
    void basicProperties() {
        assertThat(handler.getType()).isEqualTo("pptxGenerate");
        assertThat(handler.getDisplayName()).isNotEmpty();
        assertThat(handler.getCategory()).isEqualTo("Files");
        assertThat(handler.getConfigSchema()).containsKey("properties");
        assertThat(handler.getInterfaceDefinition()).containsKeys("inputs", "outputs");
    }

    @Test
    @DisplayName("slides array produces a valid, re-readable PPTX")
    void generatesPptxFromSlidesArray() throws Exception {
        NodeExecutionResult result = handler.execute(buildContext(Map.of(
                "title", "Quarterly Review",
                "theme", "warm",
                "slides", List.of(
                        Map.of("title", "Highlights",
                                "bullets", List.of("Growth +12%", "Two launches"),
                                "notes", "Speak slowly here"),
                        Map.of("title", "Next Steps",
                                "bullets", List.of("Ship documents node"))
                )
        )));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput())
                .containsKeys("artifactId", "downloadUrl", "filename")
                .containsEntry("slideCount", 2);
        assertThat(result.getOutput().get("filename")).isEqualTo("Quarterly Review.pptx");

        byte[] saved = captureSavedBytes();
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(saved))) {
            // 封面 + 2 張內容投影片
            assertThat(ppt.getSlides()).hasSize(3);
        }
    }

    @Test
    @DisplayName("markdown alternative turns headings into slides")
    void generatesPptxFromMarkdown() throws Exception {
        NodeExecutionResult result = handler.execute(buildContext(Map.of(
                "title", "Weekly Report",
                "markdown", "# Wins\n- new users\n\n# Plans\n- polish PPTX node\n- docs",
                "filename", "weekly"
        )));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("slideCount", 2);
        assertThat(result.getOutput().get("filename")).isEqualTo("weekly.pptx");

        byte[] saved = captureSavedBytes();
        try (XMLSlideShow ppt = new XMLSlideShow(new ByteArrayInputStream(saved))) {
            assertThat(ppt.getSlides()).hasSize(3);
        }
    }

    @Test
    @DisplayName("slides given as a JSON string are parsed")
    void parsesSlidesJsonString() {
        NodeExecutionResult result = handler.execute(buildContext(Map.of(
                "title", "Deck",
                "slides", "[{\"title\":\"One\",\"bullets\":[\"a\",\"b\"]}]"
        )));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsEntry("slideCount", 1);
    }

    @Test
    @DisplayName("fails clearly when both slides and markdown are missing")
    void failsWithoutContent() {
        NodeExecutionResult result = handler.execute(buildContext(Map.of("title", "Empty")));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("slides").contains("markdown");
    }

    @Test
    @DisplayName("fails clearly on invalid slides JSON")
    void failsOnInvalidSlidesJson() {
        NodeExecutionResult result = handler.execute(buildContext(Map.of(
                "slides", "not-json"
        )));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("slides");
    }

    @Test
    @DisplayName("oversized markdown input is rejected")
    void rejectsOversizedInput() {
        String huge = "x".repeat(DocumentRenderService.MAX_INPUT_CHARS + 1);
        NodeExecutionResult result = handler.execute(buildContext(Map.of("markdown", huge)));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("too large");
    }

    private byte[] captureSavedBytes() {
        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(artifactService).save(eq(USER_ID), any(ArtifactMeta.class), captor.capture());
        return captor.getValue();
    }

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("node-1")
                .nodeType("pptxGenerate")
                .nodeConfig(new HashMap<>(config))
                .userId(USER_ID)
                .flowId(UUID.randomUUID())
                .build();
    }
}
