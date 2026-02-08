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
 * Markdown processing tool
 * Supports Markdown to HTML and plain text conversion
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
                Markdown processing tool, supports multiple operations:
                - toHtml: Convert Markdown to HTML
                - toText: Convert Markdown to plain text (remove formatting)
                - extractLinks: Extract all links from Markdown
                - extractHeadings: Extract all headings

                Parameters:
                - markdown: Markdown text
                - operation: Operation type (default toHtml)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "markdown", Map.of(
                                "type", "string",
                                "description", "Markdown text"
                        ),
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("toHtml", "toText", "extractLinks", "extractHeadings"),
                                "description", "Operation type",
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
                    return ToolResult.failure("Markdown text cannot be empty");
                }

                // Security: limit input size
                if (markdown.length() > 1_000_000) {
                    return ToolResult.failure("Text too long, maximum 1MB");
                }

                return switch (operation) {
                    case "toHtml" -> toHtml(markdown);
                    case "toText" -> toPlainText(markdown);
                    case "extractLinks" -> extractLinks(markdown);
                    case "extractHeadings" -> extractHeadings(markdown);
                    default -> ToolResult.failure("Unsupported operation: " + operation);
                };

            } catch (Exception e) {
                log.error("Markdown operation failed", e);
                return ToolResult.failure("Markdown operation failed");
            }
        });
    }

    private ToolResult toHtml(String markdown) {
        String html = markdown;
        html = processCodeBlocks(html);
        html = html.replaceAll("(?m)^###### (.+)$", "<h6>$1</h6>");
        html = html.replaceAll("(?m)^##### (.+)$", "<h5>$1</h5>");
        html = html.replaceAll("(?m)^#### (.+)$", "<h4>$1</h4>");
        html = html.replaceAll("(?m)^### (.+)$", "<h3>$1</h3>");
        html = html.replaceAll("(?m)^## (.+)$", "<h2>$1</h2>");
        html = html.replaceAll("(?m)^# (.+)$", "<h1>$1</h1>");
        html = html.replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "<strong><em>$1</em></strong>");
        html = html.replaceAll("___(.+?)___", "<strong><em>$1</em></strong>");
        html = html.replaceAll("\\*\\*(.+?)\\*\\*", "<strong>$1</strong>");
        html = html.replaceAll("__(.+?)__", "<strong>$1</strong>");
        html = html.replaceAll("\\*(.+?)\\*", "<em>$1</em>");
        html = html.replaceAll("_(.+?)_", "<em>$1</em>");
        html = html.replaceAll("~~(.+?)~~", "<del>$1</del>");
        html = html.replaceAll("`([^`]+)`", "<code>$1</code>");
        html = html.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");
        html = html.replaceAll("!\\[([^\\]]*)\\]\\(([^)]+)\\)", "<img src=\"$2\" alt=\"$1\">");
        html = html.replaceAll("(?m)^[*+-] (.+)$", "<li>$1</li>");
        html = html.replaceAll("(?m)^\\d+\\. (.+)$", "<li>$1</li>");
        html = html.replaceAll("(?m)^[-*_]{3,}$", "<hr>");
        html = html.replaceAll("(?m)^> (.+)$", "<blockquote>$1</blockquote>");
        html = html.replaceAll("\\n\\n", "</p><p>");
        html = "<p>" + html + "</p>";
        html = html.replaceAll("<p></p>", "");
        String result = html.trim();

        return ToolResult.success(
                "HTML conversion result:\n" + (result.length() > 1000 ? result.substring(0, 1000) + "..." : result),
                Map.of("html", result, "length", result.length())
        );
    }

    private String processCodeBlocks(String markdown) {
        Pattern codeBlockPattern = Pattern.compile("```(\\w*)\\n([\\s\\S]*?)```");
        Matcher matcher = codeBlockPattern.matcher(markdown);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String lang = matcher.group(1);
            String code = matcher.group(2);
            code = code.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
            String replacement = String.format("<pre><code class=\"language-%s\">%s</code></pre>",
                    lang.isEmpty() ? "plaintext" : lang, code);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private ToolResult toPlainText(String markdown) {
        String text = markdown;
        text = text.replaceAll("```[\\s\\S]*?```", "");
        text = text.replaceAll("(?m)^#{1,6} ", "");
        text = text.replaceAll("\\*\\*\\*(.+?)\\*\\*\\*", "$1");
        text = text.replaceAll("___(.+?)___", "$1");
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        text = text.replaceAll("__(.+?)__", "$1");
        text = text.replaceAll("\\*(.+?)\\*", "$1");
        text = text.replaceAll("_(.+?)_", "$1");
        text = text.replaceAll("~~(.+?)~~", "$1");
        text = text.replaceAll("`([^`]+)`", "$1");
        text = text.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
        text = text.replaceAll("!\\[[^\\]]*\\]\\([^)]+\\)", "");
        text = text.replaceAll("(?m)^[*+-] ", "");
        text = text.replaceAll("(?m)^\\d+\\. ", "");
        text = text.replaceAll("(?m)^[-*_]{3,}$", "");
        text = text.replaceAll("(?m)^> ", "");
        text = text.replaceAll("\\n{3,}", "\n\n");
        text = text.trim();

        return ToolResult.success(
                "Plain text result:\n" + (text.length() > 1000 ? text.substring(0, 1000) + "..." : text),
                Map.of("text", text, "length", text.length())
        );
    }

    private ToolResult extractLinks(String markdown) {
        Pattern linkPattern = Pattern.compile("\\[([^\\]]+)\\]\\(([^)]+)\\)");
        Matcher matcher = linkPattern.matcher(markdown);

        java.util.List<Map<String, String>> links = new java.util.ArrayList<>();
        while (matcher.find()) {
            links.add(Map.of("text", matcher.group(1), "url", matcher.group(2)));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d links:\n\n", links.size()));
        for (int i = 0; i < Math.min(links.size(), 50); i++) {
            Map<String, String> link = links.get(i);
            sb.append(String.format("%d. [%s](%s)\n", i + 1, link.get("text"), link.get("url")));
        }
        if (links.size() > 50) {
            sb.append("...(remaining links omitted)\n");
        }

        return ToolResult.success(sb.toString(), Map.of("links", links, "count", links.size()));
    }

    private ToolResult extractHeadings(String markdown) {
        Pattern headingPattern = Pattern.compile("(?m)^(#{1,6}) (.+)$");
        Matcher matcher = headingPattern.matcher(markdown);

        java.util.List<Map<String, Object>> headings = new java.util.ArrayList<>();
        while (matcher.find()) {
            headings.add(Map.of("level", matcher.group(1).length(), "text", matcher.group(2)));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d headings:\n\n", headings.size()));
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
