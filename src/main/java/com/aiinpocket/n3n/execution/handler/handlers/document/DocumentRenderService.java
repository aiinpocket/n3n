package com.aiinpocket.n3n.execution.handler.handlers.document;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 文件產出共用核心：PPTX / DOCX 渲染與 Markdown 轉投影片。
 *
 * 由 PptxGenerateNodeHandler、DocxGenerateNodeHandler 與
 * GenerateDocumentTool（AI Agent 工具）共用，確保行為一致。
 */
@Service
public class DocumentRenderService {

    /** 輸入文字上限（字元數），避免惡意或失控的大輸入 */
    public static final int MAX_INPUT_CHARS = 2 * 1024 * 1024;

    /** 一張投影片的規格 */
    public record SlideSpec(String title, List<String> bullets, String notes) {
    }

    /**
     * 產生 PPTX。slides 不可為空。
     *
     * @throws IllegalArgumentException 輸入不合法時
     */
    public byte[] renderPptx(String deckTitle, List<SlideSpec> slides, String themeName) throws IOException {
        if (slides == null || slides.isEmpty()) {
            throw new IllegalArgumentException("At least one slide is required");
        }
        String title = deckTitle == null || deckTitle.isBlank() ? "Presentation" : deckTitle.trim();
        return PptxRenderer.render(title, slides, SlideTheme.of(themeName));
    }

    /**
     * 產生 DOCX。content 為 Markdown 字串。
     *
     * @throws IllegalArgumentException 輸入不合法時
     */
    public byte[] renderDocx(String title, String markdownContent) throws IOException {
        if (markdownContent == null || markdownContent.isBlank()) {
            throw new IllegalArgumentException("Content must not be empty");
        }
        checkSize(markdownContent);
        List<MarkdownDocParser.Block> blocks = MarkdownDocParser.parse(markdownContent);
        return DocxRenderer.render(title, blocks);
    }

    /**
     * 將 Markdown 轉為投影片：#/## 標題開新投影片，清單項目與段落成為 bullets。
     * 首個標題前的內容會歸入一張以簡報標題命名的投影片。
     */
    public List<SlideSpec> slidesFromMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        checkSize(markdown);

        List<SlideSpec> slides = new ArrayList<>();
        String currentTitle = null;
        List<String> currentBullets = new ArrayList<>();
        boolean hasContent = false;

        for (MarkdownDocParser.Block block : MarkdownDocParser.parse(markdown)) {
            switch (block) {
                case MarkdownDocParser.Heading heading -> {
                    if (hasContent || currentTitle != null) {
                        slides.add(new SlideSpec(currentTitle, List.copyOf(currentBullets), null));
                    }
                    currentTitle = MarkdownDocParser.plainText(heading.text());
                    currentBullets = new ArrayList<>();
                    hasContent = false;
                }
                case MarkdownDocParser.BulletList bullets -> {
                    for (String item : bullets.items()) {
                        currentBullets.add(MarkdownDocParser.plainText(item));
                    }
                    hasContent = true;
                }
                case MarkdownDocParser.NumberedList numbered -> {
                    for (String item : numbered.items()) {
                        currentBullets.add(MarkdownDocParser.plainText(item));
                    }
                    hasContent = true;
                }
                case MarkdownDocParser.Paragraph paragraph -> {
                    currentBullets.add(MarkdownDocParser.plainText(paragraph.text()));
                    hasContent = true;
                }
                case MarkdownDocParser.Table table -> {
                    for (List<String> row : table.rows()) {
                        currentBullets.add(String.join(" — ", row));
                    }
                    hasContent = true;
                }
                case MarkdownDocParser.CodeBlock code -> {
                    currentBullets.add(code.text());
                    hasContent = true;
                }
            }
        }

        if (hasContent || currentTitle != null) {
            slides.add(new SlideSpec(currentTitle, List.copyOf(currentBullets), null));
        }
        return List.copyOf(slides);
    }

    /**
     * 輸入大小防護；超過上限即拒絕。
     */
    public void checkSize(String input) {
        if (input != null && input.length() > MAX_INPUT_CHARS) {
            throw new IllegalArgumentException(
                    "Input content too large (max " + MAX_INPUT_CHARS + " characters)");
        }
    }
}
