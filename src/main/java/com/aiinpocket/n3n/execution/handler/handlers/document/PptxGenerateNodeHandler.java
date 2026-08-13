package com.aiinpocket.n3n.execution.handler.handlers.document;

import com.aiinpocket.n3n.artifact.dto.ArtifactMeta;
import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 簡報製作節點（PPTX Generate）。
 *
 * 依 slides 陣列或 Markdown 內容產生 PPTX 簡報，
 * 存入使用者個人 artifact 檔案庫並輸出下載連結。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PptxGenerateNodeHandler extends AbstractNodeHandler {

    private static final String MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final String DEFAULT_FILENAME = "presentation.pptx";

    private final DocumentRenderService renderService;
    private final ArtifactService artifactService;
    private final ObjectMapper objectMapper;

    @Override
    public String getType() {
        return "pptxGenerate";
    }

    @Override
    public String getDisplayName() {
        return "PPTX Generate";
    }

    @Override
    public String getDescription() {
        return "Generate a PowerPoint (.pptx) slide deck from a slides array or Markdown content "
                + "and save it into your artifact library. 依投影片陣列或 Markdown 產生簡報並存入作品庫。";
    }

    @Override
    public String getCategory() {
        return "Files";
    }

    @Override
    public String getIcon() {
        return "file-ppt";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String title = getStringConfig(context, "title", "").trim();
        String theme = getStringConfig(context, "theme", "warm");
        String markdown = getStringConfig(context, "markdown", "");
        Object slidesConfig = context.getNodeConfig().get("slides");

        try {
            renderService.checkSize(markdown);

            List<DocumentRenderService.SlideSpec> slides = parseSlides(slidesConfig);
            if (slides.isEmpty() && !markdown.isBlank()) {
                slides = renderService.slidesFromMarkdown(markdown);
            }
            if (slides.isEmpty()) {
                return NodeExecutionResult.failure(
                        "No slide content: provide 'slides' (array of {title, bullets, notes}) "
                                + "or 'markdown' (headings become slides, list items become bullets)");
            }

            String deckTitle = title.isBlank() ? "Presentation" : title;
            byte[] data = renderService.renderPptx(deckTitle, slides, theme);
            String filename = resolveFilename(context, deckTitle);

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
            output.put("slideCount", slides.size());
            return NodeExecutionResult.success(output);

        } catch (IllegalArgumentException e) {
            return NodeExecutionResult.failure(e.getMessage());
        } catch (Exception e) {
            log.error("PPTX generation failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("PPTX generation failed: " + sanitizeErrorMessage(e.getMessage()));
        }
    }

    /**
     * 解析 slides 設定：接受 List（表達式結果）或 JSON 字串。
     */
    @SuppressWarnings("unchecked")
    private List<DocumentRenderService.SlideSpec> parseSlides(Object slidesConfig) {
        if (slidesConfig == null) {
            return List.of();
        }

        List<Map<String, Object>> rawSlides;
        if (slidesConfig instanceof List<?> list) {
            rawSlides = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    rawSlides.add((Map<String, Object>) map);
                } else {
                    throw new IllegalArgumentException(
                            "Each slide must be an object with {title, bullets, notes}");
                }
            }
        } else if (slidesConfig instanceof String json && !json.isBlank()) {
            renderService.checkSize(json);
            try {
                rawSlides = objectMapper.readValue(
                        json, objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, Map.class));
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "'slides' is not a valid JSON array of {title, bullets, notes}");
            }
        } else {
            throw new IllegalArgumentException("'slides' must be a JSON array or an expression returning one");
        }

        List<DocumentRenderService.SlideSpec> slides = new ArrayList<>();
        for (Map<String, Object> raw : rawSlides) {
            String slideTitle = raw.get("title") instanceof String s ? s : null;
            String notes = raw.get("notes") instanceof String n ? n : null;
            List<String> bullets = new ArrayList<>();
            if (raw.get("bullets") instanceof List<?> bulletList) {
                for (Object bullet : bulletList) {
                    if (bullet != null) {
                        bullets.add(bullet.toString());
                    }
                }
            }
            slides.add(new DocumentRenderService.SlideSpec(slideTitle, List.copyOf(bullets), notes));
        }
        return List.copyOf(slides);
    }

    private String resolveFilename(NodeExecutionContext context, String deckTitle) {
        String filename = getStringConfig(context, "filename", "").trim();
        if (filename.isBlank()) {
            filename = deckTitle.isBlank() ? DEFAULT_FILENAME : deckTitle;
        }
        if (!filename.toLowerCase().endsWith(".pptx")) {
            filename = filename + ".pptx";
        }
        return filename;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("title", Map.of(
                "type", "string",
                "title", "Title / 簡報標題",
                "description", "Deck title shown on the cover slide; supports {{expressions}}. "
                        + "封面標題，支援 {{表達式}}"
        ));
        properties.put("slides", Map.of(
                "type", "array",
                "title", "Slides / 投影片",
                "items", Map.of("type", "object"),
                "description", "Array of slides: [{\"title\": \"...\", \"bullets\": [\"...\"], "
                        + "\"notes\": \"...\"}]. Accepts a JSON string or an expression returning an array. "
                        + "投影片陣列，每張含 title、bullets、選填 notes"
        ));
        properties.put("markdown", Map.of(
                "type", "string",
                "title", "Markdown 內容",
                "description", "Alternative to 'slides': Markdown where #/## headings become slides "
                        + "and list items become bullets; supports {{expressions}}. "
                        + "以 Markdown 描述簡報：標題成為投影片、清單成為要點"
        ));
        properties.put("theme", Map.of(
                "type", "string",
                "title", "Theme / 主題",
                "enum", List.of("warm", "light", "dark"),
                "default", "warm",
                "description", "Color theme: warm (paper studio palette), light, or dark. "
                        + "配色主題：warm 紙上工作室 / light 亮色 / dark 暗色"
        ));
        properties.put("filename", Map.of(
                "type", "string",
                "title", "Filename / 檔名",
                "description", "Output filename; '.pptx' is appended automatically. "
                        + "輸出檔名，會自動補上 .pptx"
        ));

        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of()
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
                                "description", "Stored filename"),
                        Map.of("name", "slideCount", "type", "integer",
                                "description", "Number of content slides")
                )
        );
    }
}
