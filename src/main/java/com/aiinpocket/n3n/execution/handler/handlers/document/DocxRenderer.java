package com.aiinpocket.n3n.execution.handler.handlers.document;

import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTblPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

/**
 * 以 Apache POI XWPF 產生 DOCX 的純渲染器。
 *
 * 將 MarkdownDocParser 的區塊模型輸出為 Word 文件：
 * 標題階層、段落（含粗體/斜體）、清單、含框線表格與程式碼區塊。
 */
final class DocxRenderer {

    private static final String HEADING_FONT = "Georgia";
    private static final String BODY_FONT = "Calibri";
    private static final String CODE_FONT = "Courier New";
    private static final String INK = "3B322A";
    private static final String ACCENT = "C0653B";
    private static final String CODE_SHADE = "F2EEE6";
    private static final int[] HEADING_SIZES = {20, 16, 13};

    private DocxRenderer() {
    }

    /**
     * 產生 DOCX 位元組。
     */
    static byte[] render(String title, List<MarkdownDocParser.Block> blocks) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            if (title != null && !title.isBlank()) {
                renderDocumentTitle(doc, title);
            }
            for (MarkdownDocParser.Block block : blocks) {
                renderBlock(doc, block);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static void renderDocumentTitle(XWPFDocument doc, String title) {
        XWPFParagraph para = doc.createParagraph();
        para.setAlignment(ParagraphAlignment.LEFT);
        para.setSpacingAfter(300);
        XWPFRun run = para.createRun();
        run.setText(title);
        run.setBold(true);
        run.setFontSize(26);
        run.setFontFamily(HEADING_FONT);
        run.setColor(ACCENT);
    }

    private static void renderBlock(XWPFDocument doc, MarkdownDocParser.Block block) {
        switch (block) {
            case MarkdownDocParser.Heading heading -> renderHeading(doc, heading);
            case MarkdownDocParser.Paragraph paragraph -> renderParagraph(doc, paragraph.text(), 0, null);
            case MarkdownDocParser.BulletList bullets -> renderList(doc, bullets.items(), "•");
            case MarkdownDocParser.NumberedList numbered -> renderNumberedList(doc, numbered.items());
            case MarkdownDocParser.Table table -> renderTable(doc, table);
            case MarkdownDocParser.CodeBlock code -> renderCodeBlock(doc, code.text());
        }
    }

    private static void renderHeading(XWPFDocument doc, MarkdownDocParser.Heading heading) {
        int level = Math.min(Math.max(heading.level(), 1), 3);
        XWPFParagraph para = doc.createParagraph();
        para.setStyle("Heading" + level);
        para.setSpacingBefore(240);
        para.setSpacingAfter(120);
        XWPFRun run = para.createRun();
        run.setText(MarkdownDocParser.plainText(heading.text()));
        run.setBold(true);
        run.setFontSize(HEADING_SIZES[level - 1]);
        run.setFontFamily(HEADING_FONT);
        run.setColor(INK);
    }

    private static void renderParagraph(XWPFDocument doc, String text, int indent, String prefix) {
        XWPFParagraph para = doc.createParagraph();
        para.setSpacingAfter(120);
        if (indent > 0) {
            para.setIndentationLeft(indent);
        }
        if (prefix != null) {
            XWPFRun prefixRun = para.createRun();
            prefixRun.setText(prefix + " ");
            prefixRun.setFontFamily(BODY_FONT);
            prefixRun.setFontSize(11);
            prefixRun.setColor(ACCENT);
        }
        for (MarkdownDocParser.InlineRun inline : MarkdownDocParser.parseInline(text)) {
            XWPFRun run = para.createRun();
            run.setText(inline.text());
            run.setBold(inline.bold());
            run.setItalic(inline.italic());
            run.setFontFamily(BODY_FONT);
            run.setFontSize(11);
            run.setColor(INK);
        }
    }

    private static void renderList(XWPFDocument doc, List<String> items, String marker) {
        for (String item : items) {
            renderParagraph(doc, item, 360, marker);
        }
    }

    private static void renderNumberedList(XWPFDocument doc, List<String> items) {
        int index = 1;
        for (String item : items) {
            renderParagraph(doc, item, 360, index++ + ".");
        }
    }

    private static void renderTable(XWPFDocument doc, MarkdownDocParser.Table table) {
        List<List<String>> rows = table.rows();
        if (rows.isEmpty()) {
            return;
        }
        int cols = rows.stream().mapToInt(List::size).max().orElse(1);
        XWPFTable xwpfTable = doc.createTable(rows.size(), cols);
        applyTableBorders(xwpfTable);

        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            XWPFTableRow tableRow = xwpfTable.getRow(r);
            boolean headerRow = table.hasHeader() && r == 0;
            for (int c = 0; c < cols; c++) {
                String cellText = c < row.size() ? row.get(c) : "";
                XWPFTableCell cell = tableRow.getCell(c);
                XWPFParagraph para = cell.getParagraphs().isEmpty()
                        ? cell.addParagraph() : cell.getParagraphs().get(0);
                XWPFRun run = para.createRun();
                run.setText(MarkdownDocParser.plainText(cellText));
                run.setBold(headerRow);
                run.setFontFamily(BODY_FONT);
                run.setFontSize(10);
                run.setColor(INK);
                if (headerRow) {
                    cell.setColor(CODE_SHADE);
                }
            }
        }
        // 表格後空一段，避免與後續內容黏在一起
        doc.createParagraph();
    }

    private static void applyTableBorders(XWPFTable table) {
        CTTblPr tblPr = table.getCTTbl().getTblPr() != null
                ? table.getCTTbl().getTblPr() : table.getCTTbl().addNewTblPr();
        CTTblBorders borders = tblPr.isSetTblBorders() ? tblPr.getTblBorders() : tblPr.addNewTblBorders();
        for (var border : List.of(
                borders.isSetTop() ? borders.getTop() : borders.addNewTop(),
                borders.isSetBottom() ? borders.getBottom() : borders.addNewBottom(),
                borders.isSetLeft() ? borders.getLeft() : borders.addNewLeft(),
                borders.isSetRight() ? borders.getRight() : borders.addNewRight(),
                borders.isSetInsideH() ? borders.getInsideH() : borders.addNewInsideH(),
                borders.isSetInsideV() ? borders.getInsideV() : borders.addNewInsideV())) {
            border.setVal(STBorder.SINGLE);
            border.setSz(BigInteger.valueOf(4));
            border.setColor("B8AE9C");
        }
    }

    private static void renderCodeBlock(XWPFDocument doc, String code) {
        for (String line : code.split("\n", -1)) {
            XWPFParagraph para = doc.createParagraph();
            para.setSpacingAfter(0);
            para.setIndentationLeft(240);
            var shd = para.getCTP().getPPr() != null
                    ? para.getCTP().getPPr().addNewShd()
                    : para.getCTP().addNewPPr().addNewShd();
            shd.setFill(CODE_SHADE);
            XWPFRun run = para.createRun();
            run.setText(line);
            run.setFontFamily(CODE_FONT);
            run.setFontSize(9);
            run.setColor(INK);
        }
        doc.createParagraph();
    }
}
