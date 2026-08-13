package com.aiinpocket.n3n.execution.handler.handlers.document;

import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.SlideLayout;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFNotes;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFSlideLayout;
import org.apache.poi.xslf.usermodel.XSLFSlideMaster;
import org.apache.poi.xslf.usermodel.XSLFTextBox;
import org.apache.poi.xslf.usermodel.XSLFTextParagraph;
import org.apache.poi.xslf.usermodel.XSLFTextRun;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * 以 Apache POI XSLF 產生 PPTX 的純渲染器。
 *
 * 版面為 16:9（1280x720 pt），確定性輸出：
 * 封面（標題頁）+ 每張投影片標題、項目符號與講者備註。
 */
final class PptxRenderer {

    private static final int PAGE_WIDTH = 1280;
    private static final int PAGE_HEIGHT = 720;
    private static final double MARGIN = 80;
    private static final String HEADING_FONT = "Georgia";
    private static final String BODY_FONT = "Calibri";
    private static final int MAX_BULLETS_PER_SLIDE = 12;

    private PptxRenderer() {
    }

    /**
     * 產生 PPTX 位元組。slides 為空時只產生封面。
     */
    static byte[] render(String deckTitle, List<DocumentRenderService.SlideSpec> slides, SlideTheme theme)
            throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            ppt.setPageSize(new Dimension(PAGE_WIDTH, PAGE_HEIGHT));
            XSLFSlideMaster master = ppt.getSlideMasters().get(0);
            XSLFSlideLayout blank = master.getLayout(SlideLayout.BLANK);

            renderTitleSlide(ppt, blank, deckTitle, slides.size(), theme);
            for (DocumentRenderService.SlideSpec spec : slides) {
                renderContentSlide(ppt, blank, spec, deckTitle, theme);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ppt.write(out);
            return out.toByteArray();
        }
    }

    private static void renderTitleSlide(XMLSlideShow ppt, XSLFSlideLayout blank,
                                         String deckTitle, int slideCount, SlideTheme theme) {
        XSLFSlide slide = ppt.createSlide(blank);
        slide.getBackground().setFillColor(theme.background());

        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle2D.Double(MARGIN, 250, PAGE_WIDTH - MARGIN * 2, 140));
        XSLFTextParagraph titlePara = titleBox.addNewTextParagraph();
        titlePara.setTextAlign(TextParagraph.TextAlign.LEFT);
        XSLFTextRun titleRun = titlePara.addNewTextRun();
        titleRun.setText(deckTitle);
        titleRun.setFontFamily(HEADING_FONT);
        titleRun.setFontSize(48.0);
        titleRun.setBold(true);
        titleRun.setFontColor(theme.ink());

        XSLFTextBox accentBar = slide.createTextBox();
        accentBar.setAnchor(new Rectangle2D.Double(MARGIN, 410, 180, 8));
        accentBar.setFillColor(theme.accent());

        XSLFTextBox subtitleBox = slide.createTextBox();
        subtitleBox.setAnchor(new Rectangle2D.Double(MARGIN, 440, PAGE_WIDTH - MARGIN * 2, 40));
        XSLFTextRun subtitleRun = subtitleBox.addNewTextParagraph().addNewTextRun();
        subtitleRun.setText(slideCount + (slideCount == 1 ? " slide" : " slides"));
        subtitleRun.setFontFamily(BODY_FONT);
        subtitleRun.setFontSize(16.0);
        subtitleRun.setFontColor(theme.accent());
    }

    private static void renderContentSlide(XMLSlideShow ppt, XSLFSlideLayout blank,
                                           DocumentRenderService.SlideSpec spec,
                                           String deckTitle, SlideTheme theme) {
        XSLFSlide slide = ppt.createSlide(blank);
        slide.getBackground().setFillColor(theme.background());

        String slideTitle = spec.title() == null || spec.title().isBlank() ? deckTitle : spec.title();

        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle2D.Double(MARGIN, 50, PAGE_WIDTH - MARGIN * 2, 70));
        XSLFTextRun titleRun = titleBox.addNewTextParagraph().addNewTextRun();
        titleRun.setText(slideTitle);
        titleRun.setFontFamily(HEADING_FONT);
        titleRun.setFontSize(32.0);
        titleRun.setBold(true);
        titleRun.setFontColor(theme.ink());

        XSLFTextBox accentBar = slide.createTextBox();
        accentBar.setAnchor(new Rectangle2D.Double(MARGIN, 125, 120, 5));
        accentBar.setFillColor(theme.accent());

        List<String> bullets = spec.bullets() == null ? List.of() : spec.bullets();
        if (!bullets.isEmpty()) {
            XSLFTextBox bodyBox = slide.createTextBox();
            bodyBox.setAnchor(new Rectangle2D.Double(
                    MARGIN, 160, PAGE_WIDTH - MARGIN * 2, PAGE_HEIGHT - 160 - 60));
            int count = 0;
            for (String bullet : bullets) {
                if (count++ >= MAX_BULLETS_PER_SLIDE) {
                    break;
                }
                XSLFTextParagraph para = bodyBox.addNewTextParagraph();
                para.setBullet(true);
                para.setBulletCharacter("•");
                para.setBulletFontColor(theme.accent());
                para.setIndentLevel(0);
                para.setSpaceAfter(10.0);
                XSLFTextRun run = para.addNewTextRun();
                run.setText(MarkdownDocParser.plainText(bullet));
                run.setFontFamily(BODY_FONT);
                run.setFontSize(20.0);
                run.setFontColor(theme.ink());
            }
        }

        if (spec.notes() != null && !spec.notes().isBlank()) {
            setNotes(ppt, slide, spec.notes());
        }
    }

    private static void setNotes(XMLSlideShow ppt, XSLFSlide slide, String notes) {
        XSLFNotes notesSlide = ppt.getNotesSlide(slide);
        for (XSLFTextShape shape : notesSlide.getPlaceholders()) {
            if (shape.getTextType() == Placeholder.BODY) {
                shape.setText(notes);
                return;
            }
        }
    }
}
