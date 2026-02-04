package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler for HTML processing nodes.
 * Parses, extracts, and manipulates HTML content.
 */
@Component
@Slf4j
public class HtmlNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "html";
    }

    @Override
    public String getDisplayName() {
        return "HTML";
    }

    @Override
    public String getDescription() {
        return "Parses and manipulates HTML content.";
    }

    @Override
    public String getCategory() {
        return "Tools";
    }

    @Override
    public String getIcon() {
        return "html5";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String operation = getStringConfig(context, "operation", "extractText");
        String html = getStringConfig(context, "html", "");

        // Get HTML from input if not in config
        if (html.isEmpty() && context.getInputData() != null) {
            Object data = context.getInputData().get("html");
            if (data != null) {
                html = data.toString();
            }
        }

        Map<String, Object> output = new HashMap<>();

        switch (operation) {
            case "extractText":
                // Strip HTML tags and extract text
                String text = html.replaceAll("<script[^>]*>[\\s\\S]*?</script>", "")
                    .replaceAll("<style[^>]*>[\\s\\S]*?</style>", "")
                    .replaceAll("<[^>]+>", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

                // Decode HTML entities
                text = decodeHtmlEntities(text);

                output.put("text", text);
                break;

            case "extractLinks":
                // Extract all href links
                List<Map<String, String>> links = new ArrayList<>();
                Pattern linkPattern = Pattern.compile("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>(.*?)</a>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
                Matcher linkMatcher = linkPattern.matcher(html);

                while (linkMatcher.find()) {
                    Map<String, String> link = new HashMap<>();
                    link.put("url", linkMatcher.group(1));
                    link.put("text", linkMatcher.group(2).replaceAll("<[^>]+>", "").trim());
                    links.add(link);
                }

                output.put("links", links);
                output.put("count", links.size());
                break;

            case "extractImages":
                // Extract all image sources
                List<Map<String, String>> images = new ArrayList<>();
                Pattern imgPattern = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE);
                Matcher imgMatcher = imgPattern.matcher(html);

                while (imgMatcher.find()) {
                    Map<String, String> img = new HashMap<>();
                    img.put("src", imgMatcher.group(1));

                    // Extract alt text
                    Pattern altPattern = Pattern.compile("alt=[\"']([^\"']*)[\"']");
                    Matcher altMatcher = altPattern.matcher(imgMatcher.group(0));
                    if (altMatcher.find()) {
                        img.put("alt", altMatcher.group(1));
                    }

                    images.add(img);
                }

                output.put("images", images);
                output.put("count", images.size());
                break;

            case "extractBySelector":
                // Simple CSS selector extraction (basic support)
                String selector = getStringConfig(context, "selector", "");
                List<String> elements = extractBySelector(html, selector);
                output.put("elements", elements);
                output.put("count", elements.size());
                break;

            case "extractMetaTags":
                // Extract meta tags
                Map<String, String> metaTags = new LinkedHashMap<>();
                Pattern metaPattern = Pattern.compile(
                    "<meta[^>]+(?:name|property)=[\"']([^\"']+)[\"'][^>]+content=[\"']([^\"']*)[\"'][^>]*>|" +
                    "<meta[^>]+content=[\"']([^\"']*)[\"'][^>]+(?:name|property)=[\"']([^\"']+)[\"'][^>]*>",
                    Pattern.CASE_INSENSITIVE);
                Matcher metaMatcher = metaPattern.matcher(html);

                while (metaMatcher.find()) {
                    String name = metaMatcher.group(1) != null ? metaMatcher.group(1) : metaMatcher.group(4);
                    String content = metaMatcher.group(2) != null ? metaMatcher.group(2) : metaMatcher.group(3);
                    if (name != null && content != null) {
                        metaTags.put(name, content);
                    }
                }

                // Extract title
                Pattern titlePattern = Pattern.compile("<title[^>]*>([^<]*)</title>", Pattern.CASE_INSENSITIVE);
                Matcher titleMatcher = titlePattern.matcher(html);
                if (titleMatcher.find()) {
                    metaTags.put("title", titleMatcher.group(1).trim());
                }

                output.put("metaTags", metaTags);
                break;

            case "convertToMarkdown":
                // Basic HTML to Markdown conversion
                String markdown = convertToMarkdown(html);
                output.put("markdown", markdown);
                break;

            case "sanitize":
                // Remove potentially dangerous elements
                String sanitized = sanitizeHtml(html);
                output.put("sanitized", sanitized);
                break;

            default:
                return NodeExecutionResult.builder()
                    .success(false)
                    .errorMessage("Unknown HTML operation: " + operation)
                    .build();
        }

        return NodeExecutionResult.builder()
            .success(true)
            .output(output)
            .build();
    }

    private String decodeHtmlEntities(String text) {
        return text
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'");
    }

    private List<String> extractBySelector(String html, String selector) {
        List<String> results = new ArrayList<>();

        // Basic tag selector (e.g., "p", "div", "h1")
        if (selector.matches("^[a-zA-Z][a-zA-Z0-9]*$")) {
            Pattern pattern = Pattern.compile("<" + selector + "[^>]*>([\\s\\S]*?)</" + selector + ">",
                Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(html);
            while (matcher.find()) {
                results.add(matcher.group(0));
            }
        }

        // Class selector (e.g., ".classname")
        else if (selector.startsWith(".")) {
            String className = selector.substring(1);
            Pattern pattern = Pattern.compile("<[^>]+class=[\"'][^\"']*\\b" + className + "\\b[^\"']*[\"'][^>]*>" +
                "([\\s\\S]*?)</[^>]+>", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(html);
            while (matcher.find()) {
                results.add(matcher.group(0));
            }
        }

        // ID selector (e.g., "#idname")
        else if (selector.startsWith("#")) {
            String id = selector.substring(1);
            Pattern pattern = Pattern.compile("<[^>]+id=[\"']" + id + "[\"'][^>]*>([\\s\\S]*?)</[^>]+>",
                Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(html);
            while (matcher.find()) {
                results.add(matcher.group(0));
            }
        }

        return results;
    }

    private String convertToMarkdown(String html) {
        String md = html;

        // Headers
        md = md.replaceAll("<h1[^>]*>([\\s\\S]*?)</h1>", "# $1\n\n");
        md = md.replaceAll("<h2[^>]*>([\\s\\S]*?)</h2>", "## $1\n\n");
        md = md.replaceAll("<h3[^>]*>([\\s\\S]*?)</h3>", "### $1\n\n");

        // Bold and italic
        md = md.replaceAll("<strong[^>]*>([\\s\\S]*?)</strong>", "**$1**");
        md = md.replaceAll("<b[^>]*>([\\s\\S]*?)</b>", "**$1**");
        md = md.replaceAll("<em[^>]*>([\\s\\S]*?)</em>", "*$1*");
        md = md.replaceAll("<i[^>]*>([\\s\\S]*?)</i>", "*$1*");

        // Links
        md = md.replaceAll("<a[^>]+href=[\"']([^\"']+)[\"'][^>]*>([\\s\\S]*?)</a>", "[$2]($1)");

        // Images
        md = md.replaceAll("<img[^>]+src=[\"']([^\"']+)[\"'][^>]+alt=[\"']([^\"']*)[\"'][^>]*>", "![$2]($1)");
        md = md.replaceAll("<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>", "![]($1)");

        // Line breaks and paragraphs
        md = md.replaceAll("<br\\s*/?>", "\n");
        md = md.replaceAll("<p[^>]*>([\\s\\S]*?)</p>", "$1\n\n");

        // Lists
        md = md.replaceAll("<li[^>]*>([\\s\\S]*?)</li>", "- $1\n");
        md = md.replaceAll("</?[ou]l[^>]*>", "\n");

        // Remove remaining tags
        md = md.replaceAll("<[^>]+>", "");

        // Clean up whitespace
        md = md.replaceAll("\n{3,}", "\n\n").trim();

        return decodeHtmlEntities(md);
    }

    private String sanitizeHtml(String html) {
        // Remove script and style tags
        String sanitized = html.replaceAll("<script[^>]*>[\\s\\S]*?</script>", "");
        sanitized = sanitized.replaceAll("<style[^>]*>[\\s\\S]*?</style>", "");

        // Remove event handlers
        sanitized = sanitized.replaceAll("\\s+on\\w+\\s*=\\s*[\"'][^\"']*[\"']", "");

        // Remove javascript: URLs
        sanitized = sanitized.replaceAll("href\\s*=\\s*[\"']javascript:[^\"']*[\"']", "href=\"#\"");

        return sanitized;
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "operation", Map.of(
                    "type", "string",
                    "title", "Operation",
                    "enum", List.of("extractText", "extractLinks", "extractImages",
                        "extractBySelector", "extractMetaTags", "convertToMarkdown", "sanitize"),
                    "default", "extractText"
                ),
                "html", Map.of(
                    "type", "string",
                    "title", "HTML Content",
                    "format", "textarea"
                ),
                "selector", Map.of(
                    "type", "string",
                    "title", "CSS Selector",
                    "description", "CSS selector for extractBySelector (supports tag, .class, #id)"
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "html", "type", "string", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "object")
            )
        );
    }
}
