package com.aiinpocket.n3n.execution.handler.handlers.document;

import com.aiinpocket.n3n.artifact.dto.ArtifactMeta;
import com.aiinpocket.n3n.artifact.entity.Artifact;
import com.aiinpocket.n3n.artifact.service.ArtifactService;
import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF 產出節點：把文字內容轉成 PDF，存入作品庫並輸出下載連結。
 *
 * 內嵌 Noto Sans TC（resources/fonts），中文/日文內容不會變成亂碼或直接炸掉——
 * PDFBox 預設的 Helvetica 完全不能編碼 CJK。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PdfGeneratorNodeHandler extends AbstractNodeHandler {

    private static final String MIME_TYPE = "application/pdf";
    private static final String FONT_RESOURCE = "/fonts/NotoSansTC-Regular.ttf";
    private static final float MARGIN = 50f;
    private static final float TITLE_SIZE = 18f;
    private static final float BODY_SIZE = 11f;
    private static final float HEADING_SIZE = 14f;
    private static final float LEADING_FACTOR = 1.5f;

    private final ArtifactService artifactService;

    @Override
    public String getType() {
        return "pdfGenerator";
    }

    @Override
    public String getDisplayName() {
        return "PDF Generate";
    }

    @Override
    public String getDescription() {
        return "Generate a PDF from text content (CJK supported via embedded font) and save it to the artifact library. "
                + "把文字內容轉成 PDF 並存入作品庫，支援中文。";
    }

    @Override
    public String getCategory() {
        return "Files";
    }

    @Override
    public String getIcon() {
        return "file-pdf";
    }

    @Override
    public boolean supportsAsync() {
        return true;
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String title = getStringConfig(context, "title", "").trim();
        String content = getStringConfig(context, "content", "");

        // 上游有 content/text 欄位時可直接沿用（報表流程常見：code 節點整理好文字再接 PDF）
        if (content.isBlank() && context.getInputData() != null) {
            Object data = context.getInputData().get("content");
            if (data == null) {
                data = context.getInputData().get("text");
            }
            if (data != null) {
                content = data.toString();
            }
        }
        if (content.isBlank()) {
            return NodeExecutionResult.failure(
                    "'content' is required: provide the text to render into the PDF — 請填要輸出成 PDF 的內容");
        }

        try (PDDocument document = new PDDocument()) {
            PDFont font = loadFont(document);
            renderText(document, font, title, content);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);

            String filename = resolveFilename(context, title);
            ArtifactMeta meta = ArtifactMeta.builder()
                    .filename(filename)
                    .mimeType(MIME_TYPE)
                    .flowId(context.getFlowId())
                    .executionId(context.getExecutionId())
                    .nodeId(context.getNodeId())
                    .sourceNodeType(getType())
                    .build();
            Artifact artifact = artifactService.save(context.getUserId(), meta, out.toByteArray());

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("artifactId", artifact.getId().toString());
            output.put("downloadUrl", ArtifactService.downloadUrl(artifact.getId()));
            output.put("filename", artifact.getFilename());
            return NodeExecutionResult.success(output);
        } catch (Exception e) {
            log.error("PDF generation failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("PDF generation failed: " + sanitizeErrorMessage(e.getMessage()));
        }
    }

    private PDFont loadFont(PDDocument document) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(FONT_RESOURCE)) {
            if (is == null) {
                throw new IOException("Embedded font not found: " + FONT_RESOURCE);
            }
            return PDType0Font.load(document, is, true);
        }
    }

    private void renderText(PDDocument document, PDFont font, String title, String content) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        float width = page.getMediaBox().getWidth() - MARGIN * 2;
        float y = page.getMediaBox().getHeight() - MARGIN;
        PDPageContentStream stream = new PDPageContentStream(document, page);

        try {
            if (!title.isBlank()) {
                for (String line : wrap(font, title, TITLE_SIZE, width)) {
                    float leading = TITLE_SIZE * LEADING_FACTOR;
                    if (y - leading < MARGIN) {
                        stream.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        y = page.getMediaBox().getHeight() - MARGIN;
                        stream = new PDPageContentStream(document, page);
                    }
                    y -= leading;
                    writeLine(stream, font, TITLE_SIZE, MARGIN, y, line);
                }
                y -= BODY_SIZE; // 標題與內文間留一行
            }

            for (String rawLine : content.split("\n", -1)) {
                // 極簡 markdown：# 開頭視為小節標題，其餘照排
                boolean heading = rawLine.startsWith("#");
                String text = heading ? rawLine.replaceFirst("^#+\\s*", "") : rawLine;
                float size = heading ? HEADING_SIZE : BODY_SIZE;
                List<String> lines = text.isBlank() ? List.of("") : wrap(font, text, size, width);
                for (String line : lines) {
                    float leading = size * LEADING_FACTOR;
                    if (y - leading < MARGIN) {
                        stream.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        y = page.getMediaBox().getHeight() - MARGIN;
                        stream = new PDPageContentStream(document, page);
                    }
                    y -= leading;
                    if (!line.isBlank()) {
                        writeLine(stream, font, size, MARGIN, y, line);
                    }
                }
            }
        } finally {
            stream.close();
        }
    }

    private void writeLine(PDPageContentStream stream, PDFont font, float size,
                           float x, float y, String text) throws IOException {
        stream.beginText();
        stream.setFont(font, size);
        stream.newLineAtOffset(x, y);
        stream.showText(stripUnsupported(font, text));
        stream.endText();
    }

    /** 字型缺字（emoji 等）以 ? 取代，避免整份文件失敗 */
    private String stripUnsupported(PDFont font, String text) {
        StringBuilder sb = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> {
            String s = new String(Character.toChars(cp));
            try {
                font.encode(s);
                sb.append(s);
            } catch (Exception e) {
                sb.append('?');
            }
        });
        return sb.toString();
    }

    /** 逐字元量寬換行（CJK 沒有空白可以斷，不能用單字換行） */
    private List<String> wrap(PDFont font, String text, float size, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        float currentWidth = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            float w;
            try {
                w = font.getStringWidth(ch) / 1000 * size;
            } catch (Exception e) {
                w = size; // 缺字時以字級估寬
            }
            if (currentWidth + w > maxWidth && current.length() > 0) {
                lines.add(current.toString());
                current.setLength(0);
                currentWidth = 0;
            }
            current.append(ch);
            currentWidth += w;
            i += Character.charCount(cp);
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private String resolveFilename(NodeExecutionContext context, String title) {
        String filename = getStringConfig(context, "filename", "").trim();
        if (filename.isBlank()) {
            filename = title.isBlank() ? "document.pdf" : title;
        }
        if (!filename.toLowerCase().endsWith(".pdf")) {
            filename = filename + ".pdf";
        }
        return filename;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("title", Map.of(
                "type", "string",
                "title", "Title",
                "description", "Rendered at the top of the PDF; supports {{expressions}}. 文件標題"
        ));
        properties.put("content", Map.of(
                "type", "string",
                "format", "textarea",
                "title", "Content",
                "description", "Text content; lines starting with # become headings. Supports {{expressions}}. "
                        + "文件內容，# 開頭的行會變成小節標題"
        ));
        properties.put("filename", Map.of("type", "string", "title", "Filename"));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("content")
        );
    }
}
