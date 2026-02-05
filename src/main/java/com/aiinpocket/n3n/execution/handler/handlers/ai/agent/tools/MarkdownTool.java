package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 處理工具
 * 支援 Markdown 轉 HTML 和純文字
 */
@Component
@Slf4j
public class MarkdownTool implements AgentNodeTool {

    @Override
    public String getId() {
        return "markdown";
    }

    @Override
    public String getName() {
        return "Markdown";
    }

    @Override
    public String getDescription() {
        return """
                Markdown 處理工具，支援多種操作：
                - toHtml: 將 Markdown 轉換為 HTML
                - toText: 將 Markdown 轉換為純文字（移除格式）
                - extractLinks: 提取 Markdown 中的所有連結
                - extractHeadings: 提取所有標題

                參數：
                - markdown: Markdown 文字
                - operation: 操作類型（預設 toHtml）
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "markdown", Map.of(
                                "type", "string",
                                "description", "Markdown 文字"
                        ),
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("toHtml", "toText", "extractLinks", "extractHeadings"),
                                "description", "操作類型",
                                "default", "toHtml"
                        )
                ),
                "required", List.of("markdown")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String markdown = (String) parameters.get("markdown");
                String operation = (String) parameters.getOrDefault("operation", "toHtml");

                if (markdown == null || markdown.isEmpty()) {
                    return ToolResult.failure("Markdown 文字不能為空");
                }

                // Security: limit input size
                if (markdown.length() > 1_000_000) {
                    return ToolResult.failure("文字過長，最大限制 1MB");
                }

                return switch (operation) {
                    case "toHtml" -> toHtml(markdown);
                    case "toText" -> toPlainText(markdown);
                    case "extractLinks" -> extractLinks(markdown);
                    case "extractHeadings" -> extractHeadings(markdown);
                    default -> ToolResult.failure("不支援的操作: " + operation);
                };

            } catch (Exception e) {
                log.error("Markdown operation failed", e);
                return ToolResult.failure("Markdown 操作失敗: " + e.getMessage());
            }
        });
    }

    private ToolResult toHtml(String markdown) {
        String html = markdown;

        // Process code blocks first (to protect them from other transformations)
        html = processCodeBlocks(html);

        // Headings
        html = html.replaceAll("(?m)^###### (.+)$", "<h6>$1</h6>");
        html = html.replaceAll("(?m)^##### (.+)$", "<h5>$1</h5>");
        html = html.replaceAll("(?m)^#### (.+)$", "<h4>$1</h4>");
        html = html.replaceAll("(?m)^### (.+)$", "<h3>$1</h3>");
        html = html.replaceAll("(?m)^## (.+)$", "<h2>$1</h2>");
        html = html.replaceAll("(?m)^# (.+)$", "<h1>$1</h1>");

        // Bold and italic
        html = html.replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "<strong><em>$1</em></strong>");
        html = html.replaceAll("___(.+?)___", "<strong><em>$1</em></strong>");
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("__(.+?)__", "<strong>$1</strong>");
        html = html.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
        html = html.replaceAll("_(.+?)_", "<em>$1</em>");

        // Strikethrough
        html = html.replaceAll("~~(.+?)~~", "<del>$1</del>");

        // Inline code
        html = html.replaceAll("`([^`]+)`", "<code>$1</code>");

        // Links
        html = html.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");

        // Images
        html = html.replaceAll("!\\[([^\\]]*)\\]\\(([^)]+)\\)", "<img src=\"$2\" alt=\"$1\">");

        // Unordered lists
        html = html.replaceAll("(?m)^[*+-] (.+)$", "<li>$1</li>");

        // Ordered lists
        html = html.replaceAll("(?m)^\\d+\\. (.+)$", "<li>$1</li>");

        // Horizontal rules
        html = html.replaceAll("(?m)^[-*_]{3,}$", "<hr>");

        // Blockquotes
        html = html.replaceAll("(?m)^> (.+)$", "<blockquote>$1</blockquote>");

        // Paragraphs (double newlines)
        html = html.replaceAll("\\n\\n", "</p><p>");
        html = "<p>" + html + "</p>";
        html = html.replaceAll("<p></p>", "");

        // Security: sanitize output (basic XSS prevention)
        // Note: In a real application, use a proper HTML sanitizer like OWASP Java HTML Sanitizer

        String result = html.trim();

        return ToolResult.success(
                "HTML 轉換結果：\n" + (result.length() > 1000 ? result.substring(0, 1000) + "..." : result),
                Map.of("html", result, "length", result.length())
        );
    }

    private String processCodeBlocks(String markdown) {
        // Fenced code blocks
        Pattern codeBlockPattern = Pattern.compile("```(\\w*)\\n([\\s\\S]*?)```");
        Matcher matcher = codeBlockPattern.matcher(markdown);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String lang = matcher.group(1);
            String code = matcher.group(2);
            // Escape HTML in code blocks
            code = code.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;");
            String replacement = String.format("<pre><code class=\"language-%s\">%s</code></pre>",
                    lang.isEmpty() ? "plaintext" : lang, code);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private ToolResult toPlainText(String markdown) {
        String text = markdown;

        // Remove code blocks
        text = text.replaceAll("```[\\s\\S]*?```", "");

        // Remove headings markers
        text = text.replaceAll("(?m)^#{1,6} ", "");

        // Remove bold/italic markers
        text = text.replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "$1");
        text = text.replaceAll("___(.+?)___", "$1");
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        text = text.replaceAll("__(.+?)__", "$1");
        text = text.replaceAll("\\*(.+?)\\*", "$1");
        text = text.replaceAll("_(.+?)_", "$1");

        // Remove strikethrough
        text = text.replaceAll("~~(.+?)~~", "$1");

        // Remove inline code markers
        text = text.replaceAll("`([^`]+)`", "$1");

        // Convert links to just text
        text = text.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");

        // Remove images
        text = text.replaceAll("!\\[[^\\]]*\\]\\([^)]+\\)", "");

        // Remove list markers
        text = text.replaceAll("(?m)^[*+-] ", "");
        text = text.replaceAll("(?m)^\\d+\\. ", "");

        // Remove horizontal rules
        text = text.replaceAll("(?m)^[-*_]{3,}$", "");

        // Remove blockquote markers
        text = text.replaceAll("(?m)^> ", "");

        // Clean up extra whitespace
        text = text.replaceAll("\\n{3,}", "\n\n");
        text = text.trim();

        return ToolResult.success(
                "純文字結果：\n" + (text.length() > 1000 ? text.substring(0, 1000) + "..." : text),
                Map.of("text", text, "length", text.length())
        );
    }

    private ToolResult extractLinks(String markdown) {
        Pattern linkPattern = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
        Matcher matcher = linkPattern.matcher(markdown);

        java.util.List<Map<String, String>> links = new java.util.ArrayList<>();
        while (matcher.find()) {
            links.add(Map.of(
                    "text", matcher.group(1),
                    "url", matcher.group(2)
            ));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("找到 %d 個連結：\n\n", links.size()));
        for (int i = 0; i < Math.min(links.size(), 50); i++) {
            Map<String, String> link = links.get(i);
            sb.append(String.format("%d. [%s](%s)\n", i + 1, link.get("text"), link.get("url")));
        }
        if (links.size() > 50) {
            sb.append("...(省略剩餘連結)\n");
        }

        return ToolResult.success(sb.toString(), Map.of("links", links, "count", links.size()));
    }

    private ToolResult extractHeadings(String markdown) {
        Pattern headingPattern = Pattern.compile("(?m)^(#{1,6}) (.+)$");
        Matcher matcher = headingPattern.matcher(markdown);

        java.util.List<Map<String, Object>> headings = new java.util.ArrayList<>();
        while (matcher.find()) {
            headings.add(Map.of(
                    "level", matcher.group(1).length(),
                    "text", matcher.group(2)
            ));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("找到 %d 個標題：\n\n", headings.size()));
        for (Map<String, Object> heading : headings) {
            int level = (int) heading.get("level");
            String indent = "  ".repeat(level - 1);
            sb.append(String.format("%s%s %s\n", indent, "#".repeat(level), heading.get("text")));
        }

        return ToolResult.success(sb.toString(), Map.of("headings", headings, "count", headings.size()));
    }

    @Override
    public String getCategory() {
        return "text";
    }
}
