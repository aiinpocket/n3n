package com.aiinpocket.n3n.execution.handler.handlers.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownDocParserTest {

    @Test
    @DisplayName("headings, paragraphs and lists parse into blocks")
    void parsesHeadingsParagraphsAndLists() {
        String md = """
                # Title One
                Some intro paragraph
                spanning two lines.

                ## Sub Title
                - bullet one
                - bullet two

                1. first
                2. second
                """;

        List<MarkdownDocParser.Block> blocks = MarkdownDocParser.parse(md);

        assertThat(blocks).hasSize(5);
        assertThat(blocks.get(0)).isEqualTo(new MarkdownDocParser.Heading(1, "Title One"));
        assertThat(blocks.get(1)).isEqualTo(
                new MarkdownDocParser.Paragraph("Some intro paragraph spanning two lines."));
        assertThat(blocks.get(2)).isEqualTo(new MarkdownDocParser.Heading(2, "Sub Title"));
        assertThat(blocks.get(3)).isEqualTo(
                new MarkdownDocParser.BulletList(List.of("bullet one", "bullet two")));
        assertThat(blocks.get(4)).isEqualTo(
                new MarkdownDocParser.NumberedList(List.of("first", "second")));
    }

    @Test
    @DisplayName("tables with separator row are detected as having a header")
    void parsesTableWithHeader() {
        String md = """
                | Name | Value |
                | --- | --- |
                | Revenue | 120 |
                | Cost | 45 |
                """;

        List<MarkdownDocParser.Block> blocks = MarkdownDocParser.parse(md);

        assertThat(blocks).hasSize(1);
        MarkdownDocParser.Table table = (MarkdownDocParser.Table) blocks.get(0);
        assertThat(table.hasHeader()).isTrue();
        assertThat(table.rows()).hasSize(3);
        assertThat(table.rows().get(0)).contains("Name", "Value");
        assertThat(table.rows().get(1)).contains("Revenue", "120");
    }

    @Test
    @DisplayName("fenced code blocks keep their content verbatim")
    void parsesCodeBlock() {
        String md = """
                Before code.

                ```
                int x = 1;
                x += 2;
                ```

                After code.
                """;

        List<MarkdownDocParser.Block> blocks = MarkdownDocParser.parse(md);

        assertThat(blocks).hasSize(3);
        MarkdownDocParser.CodeBlock code = (MarkdownDocParser.CodeBlock) blocks.get(1);
        assertThat(code.text()).isEqualTo("int x = 1;\nx += 2;");
    }

    @Test
    @DisplayName("unclosed code block is still emitted")
    void parsesUnclosedCodeBlock() {
        List<MarkdownDocParser.Block> blocks = MarkdownDocParser.parse("```\nabc");
        assertThat(blocks).containsExactly(new MarkdownDocParser.CodeBlock("abc"));
    }

    @Test
    @DisplayName("inline bold and italic are split into styled runs")
    void parsesInlineStyles() {
        List<MarkdownDocParser.InlineRun> runs =
                MarkdownDocParser.parseInline("plain **bold** and *italic* end");

        assertThat(runs).containsExactly(
                new MarkdownDocParser.InlineRun("plain ", false, false),
                new MarkdownDocParser.InlineRun("bold", true, false),
                new MarkdownDocParser.InlineRun(" and ", false, false),
                new MarkdownDocParser.InlineRun("italic", false, true),
                new MarkdownDocParser.InlineRun(" end", false, false)
        );
        assertThat(MarkdownDocParser.plainText("**bold** *it*")).isEqualTo("bold it");
    }

    @Test
    @DisplayName("null and blank input produce no blocks")
    void handlesEmptyInput() {
        assertThat(MarkdownDocParser.parse(null)).isEmpty();
        assertThat(MarkdownDocParser.parse("   \n  ")).isEmpty();
    }
}
