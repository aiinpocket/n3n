package com.aiinpocket.n3n.execution.handler.handlers.document;

import com.aiinpocket.n3n.artifact.dto.ArtifactMeta;
import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件產出節點（DOCX Generate）。
 *
 * 將 Markdown 內容轉為 Word (.docx) 文件，
 * 存入使用者個人 artifact 檔案庫並輸出下載連結。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DocxGenerateNodeHandler extends AbstractNodeHandler {

    private static final String MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String DEFAULT_FILENAME = "document.docx";

    private final DocumentRenderService renderService;
    private final ArtifactService artifactService;

    @Override
    public String getType() {
        return "docxGenerate";
    }

    @Override
    public String getDisplayName() {
        return "DOCX Generate";
    }

    @Override
    public String getDescription() {
        return "Generate a Word (.docx) document from Markdown content (headings, lists, tables, "
                + "code blocks, bold/italic) and save it into your artifact library. "
                + "將 Markdown 轉為 Word 文件並存入作品庫。";
    }

    @Override
    public String getCategory() {
        return "Files";
    }

    @Override
    public String getIcon() {
        return "file-word";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String title = getStringConfig(context, "title", "").trim();
        String content = getStringConfig(context, "content", "");

        if (content.isBlank()) {
            return NodeExecutionResult.failure(
                    "'content' is required: provide Markdown text to convert into the document");
        }

        try {
            byte[] data = renderService.renderDocx(title.isBlank() ? null : title, content);
            String filename = resolveFilename(context, title);

            ArtifactMeta meta = ArtifactMeta.builder()
                    .filename(filename)
                    .mimeType(MIME_TYPE)
                    .flowId(context.getFlowId())
                    .executionId(context.getExecutionId())
                    .nodeId(context.getNodeId())
                    .sourceNodeType(getType())
                    .build();
            Artifact artifact = artifactService.save(context.getUserId(), meta, data);

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("artifactId", artifact.getId().toString());
            output.put("downloadUrl", ArtifactService.downloadUrl(artifact.getId()));
            output.put("filename", artifact.getFilename());
            return NodeExecutionResult.success(output);

        } catch (IllegalArgumentException e) {
            return NodeExecutionResult.failure(e.getMessage());
        } catch (Exception e) {
            log.error("DOCX generation failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("DOCX generation failed: " + sanitizeErrorMessage(e.getMessage()));
        }
    }

    private String resolveFilename(NodeExecutionContext context, String title) {
        String filename = getStringConfig(context, "filename", "").trim();
        if (filename.isBlank()) {
            filename = title.isBlank() ? DEFAULT_FILENAME : title;
        }
        if (!filename.toLowerCase().endsWith(".docx")) {
            filename = filename + ".docx";
        }
        return filename;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("title", Map.of(
                "type", "string",
                "title", "Title / 文件標題",
                "description", "Document title rendered at the top; supports {{expressions}}. "
                        + "文件標題，支援 {{表達式}}"
        ));
        properties.put("content", Map.of(
                "type", "string",
                "title", "Content (Markdown) / 內容",
                "description", "Markdown content: #/##/### headings, paragraphs, - bullets, "
                        + "1. numbered lists, **bold**, *italic*, | tables |, ``` code blocks. "
                        + "Supports {{expressions}}. Markdown 內容，支援標題、清單、表格與程式碼區塊"
        ));
        properties.put("filename", Map.of(
                "type", "string",
                "title", "Filename / 檔名",
                "description", "Output filename; '.docx' is appended automatically. "
                        + "輸出檔名，會自動補上 .docx"
        ));

        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("content")
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
                "inputs", List.of(
                        Map.of("name", "input", "type", "any", "required", false,
                                "description", "Optional upstream data referenced via {{expressions}}")
                ),
                "outputs", List.of(
                        Map.of("name", "artifactId", "type", "string",
                                "description", "Saved artifact ID"),
                        Map.of("name", "downloadUrl", "type", "string",
                                "description", "Relative download URL"),
                        Map.of("name", "filename", "type", "string",
                                "description", "Stored filename")
                )
        );
    }
}
