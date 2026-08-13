package com.aiinpocket.n3n.execution.handler.handlers.document;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 極簡 Markdown 解析器（文件產出節點專用）。
 *
 * 支援的子集：
 * - #/##/### 標題
 * - 段落（連續非空白行合併）
 * - "-"/"*" 項目符號清單
 * - "1." 編號清單
 * - | a | b | 簡易表格（第二列為 |---| 分隔線時視為表頭）
 * - ``` 圍欄程式碼區塊
 * - 行內 **粗體** 與 *斜體*
 *
 * 純函式、無狀態、確定性輸出；刻意不引入外部 Markdown 函式庫。
 */
public final class MarkdownDocParser {

    private MarkdownDocParser() {
    }

    /** 區塊模型 */
    public sealed interface Block
            permits Heading, Paragraph, BulletList, NumberedList, Table, CodeBlock {
    }

    public record Heading(int level, String text) implements Block {
    }

    public record Paragraph(String text) implements Block {
    }

    public record BulletList(List<String> items) implements Block {
    }

    public record NumberedList(List<String> items) implements Block {
    }

    public record Table(List<List<String>> rows, boolean hasHeader) implements Block {
    }

    public record CodeBlock(String text) implements Block {
    }

    /** 行內文字片段（粗體/斜體標記已解析） */
    public record InlineRun(String text, boolean bold, boolean italic) {
    }

    private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.*)$");
    private static final Pattern BULLET = Pattern.compile("^\\s*[-*]\\s+(.*)$");
    private static final Pattern NUMBERED = Pattern.compile("^\\s*\\d+\\.\\s+(.*)$");
    private static final Pattern TABLE_ROW = Pattern.compile("^\\s*\\|(.*)\\|\\s*$");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\s*\\|?[\\s:|-]+\\|?\\s*$");
    private static final Pattern INLINE = Pattern.compile("(\\*\\*([^*]+)\\*\\*)|(\\*([^*]+)\\*)");

    /**
     * 將 Markdown 文字解析為區塊清單。
     */
    public static List<Block> parse(String markdown) {
        List<Block> blocks = new ArrayList<>();
        if (markdown == null || markdown.isBlank()) {
            return blocks;
        }

        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
        List<String> paragraphBuffer = new ArrayList<>();
        List<String> bulletBuffer = new ArrayList<>();
        List<String> numberedBuffer = new ArrayList<>();
        List<List<String>> tableBuffer = new ArrayList<>();
        boolean tableHasHeader = false;
        StringBuilder codeBuffer = null;

        for (String line : lines) {
            if (codeBuffer != null) {
                if (line.trim().startsWith("```")) {
                    blocks.add(new CodeBlock(stripTrailingNewline(codeBuffer.toString())));
                    codeBuffer = null;
                } else {
                    codeBuffer.append(line).append('\n');
                }
                continue;
            }

            if (line.trim().startsWith("```")) {
                flushAll(blocks, paragraphBuffer, bulletBuffer, numberedBuffer, tableBuffer, tableHasHeader);
                tableHasHeader = false;
                codeBuffer = new StringBuilder();
                continue;
            }

            Matcher tableMatcher = TABLE_ROW.matcher(line);
            if (tableMatcher.matches()) {
                flushParagraph(blocks, paragraphBuffer);
                flushLists(blocks, bulletBuffer, numberedBuffer);
                if (tableBuffer.size() == 1 && TABLE_SEPARATOR.matcher(line).matches()) {
                    tableHasHeader = true;
                } else {
                    tableBuffer.add(splitTableCells(tableMatcher.group(1)));
                }
                continue;
            }

            Matcher headingMatcher = HEADING.matcher(line);
            if (headingMatcher.matches()) {
                flushAll(blocks, paragraphBuffer, bulletBuffer, numberedBuffer, tableBuffer, tableHasHeader);
                tableHasHeader = false;
                blocks.add(new Heading(headingMatcher.group(1).length(), headingMatcher.group(2).trim()));
                continue;
            }

            Matcher bulletMatcher = BULLET.matcher(line);
            if (bulletMatcher.matches()) {
                flushParagraph(blocks, paragraphBuffer);
                flushNumbered(blocks, numberedBuffer);
                flushTable(blocks, tableBuffer, tableHasHeader);
                tableHasHeader = false;
                bulletBuffer.add(bulletMatcher.group(1).trim());
                continue;
            }

            Matcher numberedMatcher = NUMBERED.matcher(line);
            if (numberedMatcher.matches()) {
                flushParagraph(blocks, paragraphBuffer);
                flushBullets(blocks, bulletBuffer);
                flushTable(blocks, tableBuffer, tableHasHeader);
                tableHasHeader = false;
                numberedBuffer.add(numberedMatcher.group(1).trim());
                continue;
            }

            if (line.isBlank()) {
                flushAll(blocks, paragraphBuffer, bulletBuffer, numberedBuffer, tableBuffer, tableHasHeader);
                tableHasHeader = false;
                continue;
            }

            flushLists(blocks, bulletBuffer, numberedBuffer);
            flushTable(blocks, tableBuffer, tableHasHeader);
            tableHasHeader = false;
            paragraphBuffer.add(line.trim());
        }

        // 未閉合的程式碼區塊：視為程式碼結尾
        if (codeBuffer != null) {
            blocks.add(new CodeBlock(stripTrailingNewline(codeBuffer.toString())));
        }
        flushAll(blocks, paragraphBuffer, bulletBuffer, numberedBuffer, tableBuffer, tableHasHeader);

        return List.copyOf(blocks);
    }

    /**
     * 解析行內 **粗體** 與 *斜體* 標記，回傳有樣式屬性的片段。
     */
    public static List<InlineRun> parseInline(String text) {
        List<InlineRun> runs = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return runs;
        }
        Matcher matcher = INLINE.matcher(text);
        int last = 0;
        while (matcher.find()) {
            if (matcher.start() > last) {
                runs.add(new InlineRun(text.substring(last, matcher.start()), false, false));
            }
            if (matcher.group(2) != null) {
                runs.add(new InlineRun(matcher.group(2), true, false));
            } else {
                runs.add(new InlineRun(matcher.group(4), false, true));
            }
            last = matcher.end();
        }
        if (last < text.length()) {
            runs.add(new InlineRun(text.substring(last), false, false));
        }
        return List.copyOf(runs);
    }

    /**
     * 去除行內標記後的純文字（供 PPTX 項目符號使用）。
     */
    public static String plainText(String text) {
        StringBuilder sb = new StringBuilder();
        for (InlineRun run : parseInline(text)) {
            sb.append(run.text());
        }
        return sb.toString();
    }

    private static List<String> splitTableCells(String inner) {
        List<String> cells = new ArrayList<>();
        for (String cell : inner.split("\\|", -1)) {
            cells.add(cell.trim());
        }
        return cells;
    }

    private static String stripTrailingNewline(String s) {
        return s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
    }

    private static void flushAll(List<Block> blocks, List<String> paragraph,
                                 List<String> bullets, List<String> numbered,
                                 List<List<String>> table, boolean tableHasHeader) {
        flushParagraph(blocks, paragraph);
        flushLists(blocks, bullets, numbered);
        flushTable(blocks, table, tableHasHeader);
    }

    private static void flushParagraph(List<Block> blocks, List<String> paragraph) {
        if (!paragraph.isEmpty()) {
            blocks.add(new Paragraph(String.join(" ", paragraph)));
            paragraph.clear();
        }
    }

    private static void flushLists(List<Block> blocks, List<String> bullets, List<String> numbered) {
        flushBullets(blocks, bullets);
        flushNumbered(blocks, numbered);
    }

    private static void flushBullets(List<Block> blocks, List<String> bullets) {
        if (!bullets.isEmpty()) {
            blocks.add(new BulletList(List.copyOf(bullets)));
            bullets.clear();
        }
    }

    private static void flushNumbered(List<Block> blocks, List<String> numbered) {
        if (!numbered.isEmpty()) {
            blocks.add(new NumberedList(List.copyOf(numbered)));
            numbered.clear();
        }
    }

    private static void flushTable(List<Block> blocks, List<List<String>> table, boolean hasHeader) {
        if (!table.isEmpty()) {
            blocks.add(new Table(table.stream().map(List::copyOf).toList(), hasHeader));
            table.clear();
        }
    }
}
