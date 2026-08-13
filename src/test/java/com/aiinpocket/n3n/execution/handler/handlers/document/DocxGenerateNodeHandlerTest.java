package com.aiinpocket.n3n.execution.handler.handlers.document;

import com.aiinpocket.n3n.artifact.dto.ArtifactMeta;
import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocxGenerateNodeHandlerTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private ArtifactService artifactService;
    private DocxGenerateNodeHandler handler;

    @BeforeEach
    void setUp() {
        artifactService = mock(ArtifactService.class);
        handler = new DocxGenerateNodeHandler(new DocumentRenderService(), artifactService);
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
        assertThat(handler.getType()).isEqualTo("docxGenerate");
        assertThat(handler.getDisplayName()).isNotEmpty();
        assertThat(handler.getCategory()).isEqualTo("Files");
        assertThat(handler.getConfigSchema()).containsKey("properties");
        assertThat(handler.getInterfaceDefinition()).containsKeys("inputs", "outputs");
    }

    @Test
    @DisplayName("markdown with headings, lists, table and code produces a valid DOCX")
    void generatesValidDocx() throws Exception {
        String markdown = """
                # Summary
                Overall a **stable** month with *steady* growth.

                ## Numbers
                | Metric | Value |
                | --- | --- |
                | Revenue | 1.2M |

                ## Actions
                - keep strategy
                1. boost marketing

                ```
                SELECT * FROM sales;
                ```
                """;

        NodeExecutionResult result = handler.execute(buildContext(Map.of(
                "title", "Monthly Report",
                "content", markdown,
                "filename", "monthly-report"
        )));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput()).containsKeys("artifactId", "downloadUrl");
        assertThat(result.getOutput().get("filename")).isEqualTo("monthly-report.docx");

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(artifactService).save(eq(USER_ID), any(ArtifactMeta.class), captor.capture());

        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(captor.getValue()));
             XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
            String text = extractor.getText();
            assertThat(text).contains("Monthly Report", "Summary", "stable", "steady",
                    "keep strategy", "boost marketing", "SELECT * FROM sales;");
            assertThat(doc.getTables()).hasSize(1);
            assertThat(doc.getTables().get(0).getRow(0).getCell(0).getText()).isEqualTo("Metric");
            assertThat(doc.getTables().get(0).getRows()).hasSize(2);
        }
    }

    @Test
    @DisplayName("fails clearly when content is missing")
    void failsWithoutContent() {
        NodeExecutionResult result = handler.execute(buildContext(Map.of("title", "No body")));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("content");
    }

    @Test
    @DisplayName("oversized content is rejected")
    void rejectsOversizedContent() {
        String huge = "y".repeat(DocumentRenderService.MAX_INPUT_CHARS + 1);
        NodeExecutionResult result = handler.execute(buildContext(Map.of("content", huge)));

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("too large");
    }

    @Test
    @DisplayName("filename defaults to title with .docx appended")
    void defaultsFilenameFromTitle() {
        NodeExecutionResult result = handler.execute(buildContext(Map.of(
                "title", "Notes",
                "content", "# Hello\nWorld"
        )));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutput().get("filename")).isEqualTo("Notes.docx");
    }

    private NodeExecutionContext buildContext(Map<String, Object> config) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("node-1")
                .nodeType("docxGenerate")
                .nodeConfig(new HashMap<>(config))
                .userId(USER_ID)
                .flowId(UUID.randomUUID())
                .build();
    }
}
