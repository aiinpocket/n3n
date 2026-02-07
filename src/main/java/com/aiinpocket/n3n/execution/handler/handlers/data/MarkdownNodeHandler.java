package com.aiinpocket.n3n.execution.handler.handlers.data;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler for Markdown data transformation nodes.
 * Provides operations to convert markdown to HTML, strip markdown to plain text,
 * and extract structured elements like headings and links.
 * Uses regex-based implementation with no external dependencies.
 */
@Component
@Slf4j
public class MarkdownNodeHandler extends AbstractNodeHandler {

    // Regex patterns for markdown elements
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[([^\\]]*)]\\(([^)]+)\\)");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*|__(.+?)__");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)|(?<!_)_(?!_)(.+?)(?<!_)_(?!_)");
    private static final Pattern STRIKETHROUGH_PATTERN = Pattern.compile("~~(.+?)~~");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w*)\\n([\\s\\S]*?)```", Pattern.MULTILINE);
    private static final Pattern BLOCKQUOTE_PATTERN = Pattern.compile("^>\\s?(.+)$", Pattern.MULTILINE);
    private static final Pattern UNORDERED_LIST_PATTERN = Pattern.compile("^[*+-]\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern ORDERED_LIST_PATTERN = Pattern.compile("^\\d+\\.\\s+(.+)$", Pattern.MULTILINE);
    private static final Pattern HORIZONTAL_RULE_PATTERN = Pattern.compile("^([-*_]){3,}\\s*$", Pattern.MULTILINE);

    @Override
    public String getType() {
        return "markdown";
    }

    @Override
    public String getDisplayName() {
        return "Markdown";
    }

    @Override
    public String getDescription() {
        return "Convert and extract data from Markdown text.";
    }

    @Override
    public String getCategory() {
        return "Data Transformation";
    }

    @Override
    public String getIcon() {
        return "file-text";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String operation = getStringConfig(context, "operation", "toHtml");
        String input = getStringConfig(context, "input", "");

        // If input is empty, try to get from input data
        if (input.isEmpty() && context.getInputData() != null) {
            Object data = context.getInputData().get("data");
            if (data == null) {
                data = context.getInputData().get("text");
            }
            if (data == null) {
                data = context.getInputData().get("markdown");
            }
            if (data != null) {
                input = data.toString();
            }
        }

        if (input.isEmpty()) {
            return NodeExecutionResult.failure("Markdown input is empty");
        }

        try {
            return switch (operation) {
                case "toHtml" -> convertToHtml(input, context);
                case "toText" -> convertToPlainText(input);
                case "extractHeadings" -> extractHeadings(input);
                case "extractLinks" -> extractLinks(input);
                default -> NodeExecutionResult.failure("Unknown markdown operation: " + operation);
            };
        } catch (Exception e) {
            log.error("Markdown operation '{}' failed: {}", operation, e.getMessage(), e);
            return NodeExecutionResult.failure("Markdown operation failed: " + e.getMessage());
        }
    }

    /**
     * Convert markdown text to HTML.
     */
    private NodeExecutionResult convertToHtml(String markdown, NodeExecutionContext context) {
        boolean wrapInDiv = getBooleanConfig(context, "wrapInDiv", false);

        String html = markdownToHtml(markdown);

        if (wrapInDiv) {
            html = "<div class=\"markdown-content\">\n" + html + "\n</div>";
        }

        Map<String, Object> output = new HashMap<>();
        output.put("html", html);
        output.put("length", html.length());

        return NodeExecutionResult.success(output);
    }

    /**
     * Convert markdown to HTML using regex transformations.
     */
    private String markdownToHtml(String markdown) {
        String result = markdown;

        // Process code blocks first (to protect their content from other transformations)
        List<String> codeBlocks = new ArrayList<>();
        Matcher codeBlockMatcher = CODE_BLOCK_PATTERN.matcher(result);
        StringBuilder codeBlockResult = new StringBuilder();
        while (codeBlockMatcher.find()) {
            String lang = codeBlockMatcher.group(1);
            String code = escapeHtml(codeBlockMatcher.group(2));
            String replacement;
            if (lang != null && !lang.isEmpty()) {
                replacement = "\u0000CODEBLOCK" + codeBlocks.size() + "\u0000";
                codeBlocks.add("<pre><code class=\"language-" + lang + "\">" + code + "</code></pre>");
            } else {
                replacement = "\u0000CODEBLOCK" + codeBlocks.size() + "\u0000";
                codeBlocks.add("<pre><code>" + code + "</code></pre>");
            }
            codeBlockMatcher.appendReplacement(codeBlockResult, Matcher.quoteReplacement(replacement));
        }
        codeBlockMatcher.appendTail(codeBlockResult);
        result = codeBlockResult.toString();

        // Inline code
        List<String> inlineCodes = new ArrayList<>();
        Matcher inlineCodeMatcher = INLINE_CODE_PATTERN.matcher(result);
        StringBuilder inlineCodeResult = new StringBuilder();
        while (inlineCodeMatcher.find()) {
            String replacement = "\u0000INLINECODE" + inlineCodes.size() + "\u0000";
            inlineCodes.add("<code>" + escapeHtml(inlineCodeMatcher.group(1)) + "</code>");
            inlineCodeMatcher.appendReplacement(inlineCodeResult, Matcher.quoteReplacement(replacement));
        }
        inlineCodeMatcher.appendTail(inlineCodeResult);
        result = inlineCodeResult.toString();

        // Horizontal rules (before headings to avoid conflicts)
        result = HORIZONTAL_RULE_PATTERN.matcher(result).replaceAll("<hr>");

        // Headings
        result = HEADING_PATTERN.matcher(result).replaceAll(mr -> {
            int level = mr.group(1).length();
            String text = mr.group(2).trim();
            return "<h" + level + ">" + text + "</h" + level + ">";
        });

        // Images (before links, since images contain link-like syntax)
        result = IMAGE_PATTERN.matcher(result).replaceAll("<img src=\"$2\" alt=\"$1\">");

        // Links
        result = LINK_PATTERN.matcher(result).replaceAll("<a href=\"$2\">$1</a>");

        // Bold
        result = BOLD_PATTERN.matcher(result).replaceAll(mr -> {
            String content = mr.group(1) != null ? mr.group(1) : mr.group(2);
            return "<strong>" + content + "</strong>";
        });

        // Italic
        result = ITALIC_PATTERN.matcher(result).replaceAll(mr -> {
            String content = mr.group(1) != null ? mr.group(1) : mr.group(2);
            return "<em>" + content + "</em>";
        });

        // Strikethrough
        result = STRIKETHROUGH_PATTERN.matcher(result).replaceAll("<del>$1</del>");

        // Blockquotes
        result = BLOCKQUOTE_PATTERN.matcher(result).replaceAll("<blockquote>$1</blockquote>");

        // Unordered lists
        result = processUnorderedLists(result);

        // Ordered lists
        result = processOrderedLists(result);

        // Paragraphs - wrap remaining text blocks in <p> tags
        result = processParagraphs(result);

        // Restore code blocks
        for (int i = 0; i < codeBlocks.size(); i++) {
            result = result.replace("\u0000CODEBLOCK" + i + "\u0000", codeBlocks.get(i));
        }

        // Restore inline code
        for (int i = 0; i < inlineCodes.size(); i++) {
            result = result.replace("\u0000INLINECODE" + i + "\u0000", inlineCodes.get(i));
        }

        return result.trim();
    }

    /**
     * Process unordered list items into HTML lists.
     */
    private String processUnorderedLists(String text) {
        StringBuilder result = new StringBuilder();
        String[] lines = text.split("\n");
        boolean inList = false;

        for (String line : lines) {
            Matcher matcher = UNORDERED_LIST_PATTERN.matcher(line);
            if (matcher.matches()) {
                if (!inList) {
                    result.append("<ul>\n");
                    inList = true;
                }
                result.append("<li>").append(matcher.group(1)).append("</li>\n");
            } else {
                if (inList) {
                    result.append("</ul>\n");
                    inList = false;
                }
                result.append(line).append("\n");
            }
        }
        if (inList) {
            result.append("</ul>\n");
        }

        return result.toString();
    }

    /**
     * Process ordered list items into HTML lists.
     */
    private String processOrderedLists(String text) {
        StringBuilder result = new StringBuilder();
        String[] lines = text.split("\n");
        boolean inList = false;

        for (String line : lines) {
            Matcher matcher = ORDERED_LIST_PATTERN.matcher(line);
            if (matcher.matches()) {
                if (!inList) {
                    result.append("<ol>\n");
                    inList = true;
                }
                result.append("<li>").append(matcher.group(1)).append("</li>\n");
            } else {
                if (inList) {
                    result.append("</ol>\n");
                    inList = false;
                }
                result.append(line).append("\n");
            }
        }
        if (inList) {
            result.append("</ol>\n");
        }

        return result.toString();
    }

    /**
     * Wrap remaining text blocks in paragraph tags.
     */
    private String processParagraphs(String text) {
        String[] blocks = text.split("\n\n+");
        StringBuilder result = new StringBuilder();

        for (String block : blocks) {
            String trimmed = block.trim();
            if (trimmed.isEmpty()) continue;

            // Don't wrap block-level elements in <p>
            if (trimmed.startsWith("<h") || trimmed.startsWith("<ul") || trimmed.startsWith("<ol")
                || trimmed.startsWith("<pre") || trimmed.startsWith("<blockquote")
                || trimmed.startsWith("<hr") || trimmed.startsWith("<div")
                || trimmed.startsWith("<table") || trimmed.startsWith("\u0000CODEBLOCK")) {
                result.append(trimmed).append("\n");
            } else {
                // Replace single newlines with <br> within paragraphs
                String paragraph = trimmed.replace("\n", "<br>\n");
                result.append("<p>").append(paragraph).append("</p>\n");
            }
        }

        return result.toString();
    }

    /**
     * Strip all markdown formatting and return plain text.
     */
    private NodeExecutionResult convertToPlainText(String markdown) {
        String text = markdown;

        // Remove code blocks
        text = CODE_BLOCK_PATTERN.matcher(text).replaceAll("$2");

        // Remove inline code backticks
        text = INLINE_CODE_PATTERN.matcher(text).replaceAll("$1");

        // Remove images, keep alt text
        text = IMAGE_PATTERN.matcher(text).replaceAll("$1");

        // Remove links, keep link text
        text = LINK_PATTERN.matcher(text).replaceAll("$1");

        // Remove bold markers
        text = BOLD_PATTERN.matcher(text).replaceAll(mr -> {
            String content = mr.group(1) != null ? mr.group(1) : mr.group(2);
            return content;
        });

        // Remove italic markers
        text = ITALIC_PATTERN.matcher(text).replaceAll(mr -> {
            String content = mr.group(1) != null ? mr.group(1) : mr.group(2);
            return content;
        });

        // Remove strikethrough
        text = STRIKETHROUGH_PATTERN.matcher(text).replaceAll("$1");

        // Remove heading markers
        text = HEADING_PATTERN.matcher(text).replaceAll("$2");

        // Remove blockquote markers
        text = BLOCKQUOTE_PATTERN.matcher(text).replaceAll("$1");

        // Remove horizontal rules
        text = HORIZONTAL_RULE_PATTERN.matcher(text).replaceAll("");

        // Remove list markers
        text = UNORDERED_LIST_PATTERN.matcher(text).replaceAll("$1");
        text = ORDERED_LIST_PATTERN.matcher(text).replaceAll("$1");

        // Clean up extra whitespace
        text = text.replaceAll("\n{3,}", "\n\n").trim();

        Map<String, Object> output = new HashMap<>();
        output.put("text", text);
        output.put("length", text.length());
        output.put("wordCount", text.split("\\s+").length);

        return NodeExecutionResult.success(output);
    }

    /**
     * Extract all headings from markdown text.
     */
    private NodeExecutionResult extractHeadings(String markdown) {
        Matcher matcher = HEADING_PATTERN.matcher(markdown);
        List<Map<String, Object>> headings = new ArrayList<>();

        while (matcher.find()) {
            int level = matcher.group(1).length();
            String text = matcher.group(2).trim();

            Map<String, Object> heading = new LinkedHashMap<>();
            heading.put("level", level);
            heading.put("text", text);
            // Generate a simple slug for anchor links
            heading.put("slug", text.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .trim());
            headings.add(heading);
        }

        Map<String, Object> output = new HashMap<>();
        output.put("headings", headings);
        output.put("count", headings.size());

        // Build a table of contents structure
        List<Map<String, Object>> toc = buildTableOfContents(headings);
        output.put("tableOfContents", toc);

        return NodeExecutionResult.success(output);
    }

    /**
     * Build a hierarchical table of contents from flat headings list.
     */
    private List<Map<String, Object>> buildTableOfContents(List<Map<String, Object>> headings) {
        List<Map<String, Object>> toc = new ArrayList<>();
        for (Map<String, Object> heading : headings) {
            Map<String, Object> tocEntry = new LinkedHashMap<>();
            tocEntry.put("level", heading.get("level"));
            tocEntry.put("text", heading.get("text"));
            tocEntry.put("slug", heading.get("slug"));
            toc.add(tocEntry);
        }
        return toc;
    }

    /**
     * Extract all links from markdown text.
     */
    private NodeExecutionResult extractLinks(String markdown) {
        List<Map<String, Object>> links = new ArrayList<>();
        List<Map<String, Object>> images = new ArrayList<>();

        // Extract images first
        Matcher imageMatcher = IMAGE_PATTERN.matcher(markdown);
        while (imageMatcher.find()) {
            Map<String, Object> image = new LinkedHashMap<>();
            image.put("alt", imageMatcher.group(1));
            image.put("url", imageMatcher.group(2));
            image.put("type", "image");
            images.add(image);
        }

        // Extract links
        Matcher linkMatcher = LINK_PATTERN.matcher(markdown);
        while (linkMatcher.find()) {
            // Skip if this is part of an image markdown
            int start = linkMatcher.start();
            if (start > 0 && markdown.charAt(start - 1) == '!') {
                continue;
            }

            Map<String, Object> link = new LinkedHashMap<>();
            link.put("text", linkMatcher.group(1));
            link.put("url", linkMatcher.group(2));

            // Classify the link
            String url = linkMatcher.group(2);
            if (url.startsWith("http://") || url.startsWith("https://")) {
                link.put("type", "external");
            } else if (url.startsWith("mailto:")) {
                link.put("type", "email");
            } else if (url.startsWith("#")) {
                link.put("type", "anchor");
            } else {
                link.put("type", "relative");
            }

            links.add(link);
        }

        Map<String, Object> output = new HashMap<>();
        output.put("links", links);
        output.put("images", images);
        output.put("linkCount", links.size());
        output.put("imageCount", images.size());

        return NodeExecutionResult.success(output);
    }

    /**
     * Escape HTML special characters.
     */
    private String escapeHtml(String text) {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();

        properties.put("operation", Map.of(
            "type", "string",
            "title", "Operation",
            "description", "Markdown operation to perform",
            "enum", List.of("toHtml", "toText", "extractHeadings", "extractLinks"),
            "enumNames", List.of(
                "To HTML (convert markdown to HTML)",
                "To Text (strip markdown to plain text)",
                "Extract Headings (get all headings with levels)",
                "Extract Links (get all links and images)"
            ),
            "default", "toHtml"
        ));

        properties.put("input", Map.of(
            "type", "string",
            "title", "Markdown Input",
            "description", "Markdown text to process"
        ));

        properties.put("wrapInDiv", Map.of(
            "type", "boolean",
            "title", "Wrap in Div",
            "description", "Wrap HTML output in a div with class 'markdown-content'",
            "default", false
        ));

        return Map.of(
            "type", "object",
            "properties", properties
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "string", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "any")
            )
        );
    }
}
