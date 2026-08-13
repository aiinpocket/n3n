package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.artifact.dto.ArtifactMeta;
import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import com.aiinpocket.n3n.execution.handler.handlers.document.DocumentRenderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Agent tool：由 Markdown 產生 PPTX 簡報或 DOCX 文件，
 * 存入呼叫者本人的 artifact 檔案庫（無使用者時直接拒絕）。
 *
 * 與 pptxGenerate / docxGenerate 節點共用 DocumentRenderService 核心。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GenerateDocumentTool implements AgentNodeTool {

    private static final String PPTX_MIME =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final DocumentRenderService renderService;
    private final ArtifactService artifactService;

    @Override
    public String getId() {
        return "generate_document";
    }

    @Override
    public String getName() {
        return "Generate Document";
    }

    @Override
    public String getDescription() {
        return """
                Generates a PowerPoint (.pptx) slide deck or a Word (.docx) document from
                Markdown and saves it into the current user's artifact library, returning
                the artifactId and a download URL. For pptx, #/## headings become slides
                and list items become bullets. For docx, the Markdown subset supported is
                headings, paragraphs, bullet/numbered lists, **bold**, *italic*,
                | tables | and ``` code blocks.

                Parameters:
                - format: "pptx" or "docx" (required)
                - title: Document/deck title
                - markdown: Markdown content (required)
                - filename: Optional output filename (extension appended automatically)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "format", Map.of(
                                "type", "string",
                                "enum", List.of("pptx", "docx"),
                                "description", "Output format: pptx (slides) or docx (document)"
                        ),
                        "title", Map.of(
                                "type", "string",
                                "description", "Title of the deck or document"
                        ),
                        "markdown", Map.of(
                                "type", "string",
                                "description", "Markdown content to render"
                        ),
                        "filename", Map.of(
                                "type", "string",
                                "description", "Optional output filename; extension is appended automatically"
                        )
                ),
                "required", List.of("format", "markdown")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> doExecute(parameters, context));
    }

    private ToolResult doExecute(Map<String, Object> parameters, ToolExecutionContext context) {
        UUID userId = ToolSecurity.parseUserId(context);
        if (userId == null) {
            return ToolResult.failure("No authenticated user in execution context");
        }

        String format = parameters.get("format") instanceof String f ? f.trim().toLowerCase() : "";
        if (!"pptx".equals(format) && !"docx".equals(format)) {
            return ToolResult.failure("Invalid format: must be \"pptx\" or \"docx\"");
        }

        String markdown = parameters.get("markdown") instanceof String m && !m.isBlank() ? m : null;
        if (markdown == null) {
            return ToolResult.failure("Missing required parameter: markdown");
        }

        String title = parameters.get("title") instanceof String t && !t.isBlank() ? t.trim() : null;

        byte[] data;
        try {
            if ("pptx".equals(format)) {
                List<DocumentRenderService.SlideSpec> slides = renderService.slidesFromMarkdown(markdown);
                if (slides.isEmpty()) {
                    return ToolResult.failure("Markdown produced no slides — add headings or content");
                }
                data = renderService.renderPptx(title, slides, "warm");
            } else {
                data = renderService.renderDocx(title, markdown);
            }
        } catch (IllegalArgumentException e) {
            return ToolResult.failure("Cannot generate document: " + e.getMessage());
        } catch (Exception e) {
            log.error("generate_document tool failed for user {}", userId, e);
            return ToolResult.failure("Document generation failed");
        }

        String filename = resolveFilename(parameters, title, format);
        ArtifactMeta meta = ArtifactMeta.builder()
                .filename(filename)
                .mimeType("pptx".equals(format) ? PPTX_MIME : DOCX_MIME)
                .flowId(ToolSecurity.parseUuid(context.flowId()))
                .executionId(ToolSecurity.parseUuid(context.executionId()))
                .sourceNodeType("aiAgent")
                .build();

        try {
            Artifact artifact = artifactService.save(userId, meta, data);
            String downloadUrl = ArtifactService.downloadUrl(artifact.getId());
            return ToolResult.success(
                    "Document generated: " + artifact.getFilename()
                            + " (id: " + artifact.getId()
                            + ", size: " + artifact.getSizeBytes() + " bytes)\n"
                            + "Download URL: " + downloadUrl,
                    Map.of(
                            "artifactId", artifact.getId().toString(),
                            "filename", artifact.getFilename(),
                            "downloadUrl", downloadUrl,
                            "sizeBytes", artifact.getSizeBytes()
                    ));
        } catch (IllegalArgumentException e) {
            return ToolResult.failure("Cannot save artifact: " + e.getMessage());
        } catch (Exception e) {
            log.error("generate_document artifact save failed for user {}", userId, e);
            return ToolResult.failure("Failed to save generated document");
        }
    }

    private String resolveFilename(Map<String, Object> parameters, String title, String format) {
        String filename = parameters.get("filename") instanceof String f && !f.isBlank()
                ? f.trim()
                : (title != null ? title : "document");
        String extension = "." + format;
        if (!filename.toLowerCase().endsWith(extension)) {
            filename = filename + extension;
        }
        return filename;
    }

    @Override
    public int getTimeoutSeconds() {
        return 60;
    }

    @Override
    public String getCategory() {
        return "platform";
    }
}
