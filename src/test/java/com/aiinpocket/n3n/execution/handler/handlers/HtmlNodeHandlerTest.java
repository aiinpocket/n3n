package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

class HtmlNodeHandlerTest {

    private HtmlNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new HtmlNodeHandler();
    }

    @Nested
    @DisplayName("Basic Properties")
    class BasicProperties {
        @Test
        void getType_returnsHtml() {
            assertThat(handler.getType()).isEqualTo("html");
        }

        @Test
        void getDisplayName_returnsHTML() {
            assertThat(handler.getDisplayName()).isEqualTo("HTML");
        }

        @Test
        void getCategory_returnsTools() {
            assertThat(handler.getCategory()).isEqualTo("Tools");
        }

        @Test
        void getConfigSchema_containsProperties() {
            var schema = handler.getConfigSchema();
            assertThat(schema).containsKey("properties");
        }

        @Test
        void getInterfaceDefinition_hasInputsAndOutputs() {
            var iface = handler.getInterfaceDefinition();
            assertThat(iface).containsKey("inputs");
            assertThat(iface).containsKey("outputs");
        }
    }

    @Nested
    @DisplayName("Extract Text")
    class ExtractText {
        @Test
        void execute_extractText_stripsHtmlTags() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "extractText");
            config.put("html", "<p>Hello <b>World</b></p>");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("text").toString()).contains("Hello");
            assertThat(result.getOutput().get("text").toString()).contains("World");
        }

        @Test
        void execute_extractText_removesScriptTags() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "extractText");
            config.put("html", "<p>Text</p><script>alert('xss')</script>");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("text").toString()).doesNotContain("alert");
        }

        @Test
        void execute_extractText_removesStyleTags() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "extractText");
            config.put("html", "<style>.red{color:red}</style><p>Content</p>");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("text").toString()).doesNotContain("color");
            assertThat(result.getOutput().get("text").toString()).contains("Content");
        }

        @Test
        void execute_extractText_decodesHtmlEntities() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "extractText");
            config.put("html", "<p>A &amp; B &lt; C &gt; D</p>");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("text").toString()).contains("A & B < C > D");
        }

        @Test
        void execute_extractText_fromInputData() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "extractText");
            Map<String, Object> input = new HashMap<>();
            input.put("html", "<div>From input</div>");

            NodeExecutionResult result = handler.execute(buildContext(config, input));

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutput().get("text").toString()).contains("From input");
        }
    }

    @Nested
    @DisplayName("Extract Links")
    class ExtractLinks {
        @Test
        @SuppressWarnings("unchecked")
        void execute_extractLinks_findsAllLinks() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "extractLinks");
            config.put("html", "<a href=\"https://example.com\">Example</a> <a href=\"/page\">Page</a>");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isTrue();
            List<Map<String, String>> links = (List<Map<String, String>>) result.getOutput().get("links");
            assertThat(links).hasSize(2);
            assertThat(links.get(0).get("url")).isEqualTo("https://example.com");
            assertThat(links.get(0).get("text")).isEqualTo("Example");
            assertThat(result.getOutput().get("count")).isEqualTo(2);
        }

        @Test
        @SuppressWarnings("unchecked")
        void execute_extractLinks_noLinks_returnsEmpty() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "extractLinks");
            config.put("html", "<p>No links here</p>");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isTrue();
            List<Map<String, String>> links = (List<Map<String, String>>) result.getOutput().get("links");
            assertThat(links).isEmpty();
        }
    }

    @Nested
    @DisplayName("Extract Images")
    class ExtractImages {
        @Test
        @SuppressWarnings("unchecked")
        void execute_extractImages_findsAllImages() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "extractImages");
            config.put("html", "<img src=\"img1.png\" alt=\"Image 1\"><img src=\"img2.jpg\">");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isTrue();
            List<Map<String, String>> images = (List<Map<String, String>>) result.getOutput().get("images");
            assertThat(images).hasSize(2);
            assertThat(images.get(0).get("src")).isEqualTo("img1.png");
            assertThat(images.get(0).get("alt")).isEqualTo("Image 1");
            assertThat(result.getOutput().get("count")).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("Extract Meta Tags")
    class ExtractMetaTags {
        @Test
        @SuppressWarnings("unchecked")
        void execute_extractMetaTags_extractsMetaAndTitle() {
            String html = "<html><head><title>My Page</title>" +
                "<meta name=\"description\" content=\"A description\">" +
                "<meta property=\"og:title\" content=\"OG Title\">" +
                "</head></html>";

            Map<String, Object> config = new HashMap<>();
            config.put("operation", "extractMetaTags");
            config.put("html", html);

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isTrue();
            Map<String, String> metaTags = (Map<String, String>) result.getOutput().get("metaTags");
            assertThat(metaTags).containsEntry("title", "My Page");
            assertThat(metaTags).containsEntry("description", "A description");
        }
    }

    @Nested
    @DisplayName("Convert to Markdown")
    class ConvertToMarkdown {
        @Test
        void execute_convertToMarkdown_convertsHeaders() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "convertToMarkdown");
            config.put("html", "<h1>Title</h1><h2>Subtitle</h2>");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isTrue();
            String md = result.getOutput().get("markdown").toString();
            assertThat(md).contains("# Title");
            assertThat(md).contains("## Subtitle");
        }

        @Test
        void execute_convertToMarkdown_convertsBoldAndItalic() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "convertToMarkdown");
            config.put("html", "<strong>bold</strong> and <em>italic</em>");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isTrue();
            String md = result.getOutput().get("markdown").toString();
            assertThat(md).contains("**bold**");
            assertThat(md).contains("*italic*");
        }

        @Test
        void execute_convertToMarkdown_convertsLinks() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "convertToMarkdown");
            config.put("html", "<a href=\"https://example.com\">Click here</a>");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isTrue();
            String md = result.getOutput().get("markdown").toString();
            assertThat(md).contains("[Click here](https://example.com)");
        }
    }

    @Nested
    @DisplayName("Sanitize HTML")
    class SanitizeHtml {
        @Test
        void execute_sanitize_removesScriptTags() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "sanitize");
            config.put("html", "<p>Safe</p><script>alert('xss')</script>");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isTrue();
            String sanitized = result.getOutput().get("sanitized").toString();
            assertThat(sanitized).doesNotContain("<script");
            assertThat(sanitized).contains("<p>Safe</p>");
        }

        @Test
        void execute_sanitize_removesEventHandlers() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "sanitize");
            config.put("html", "<div onclick=\"alert('x')\">Click</div>");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isTrue();
            String sanitized = result.getOutput().get("sanitized").toString();
            assertThat(sanitized).doesNotContain("onclick");
        }

        @Test
        void execute_sanitize_removesJavascriptUrls() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "sanitize");
            config.put("html", "<a href=\"javascript:alert('xss')\">Link</a>");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isTrue();
            String sanitized = result.getOutput().get("sanitized").toString();
            assertThat(sanitized).doesNotContain("javascript:");
        }
    }

    @Nested
    @DisplayName("Extract by Selector")
    class ExtractBySelector {
        @Test
        @SuppressWarnings("unchecked")
        void execute_extractBySelector_tagSelector() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "extractBySelector");
            config.put("html", "<div><p>First</p><p>Second</p></div>");
            config.put("selector", "p");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isTrue();
            List<String> elements = (List<String>) result.getOutput().get("elements");
            assertThat(elements).hasSize(2);
        }

        @Test
        @SuppressWarnings("unchecked")
        void execute_extractBySelector_classSelector() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "extractBySelector");
            config.put("html", "<div class=\"highlight\">Found</div><div>Not</div>");
            config.put("selector", ".highlight");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isTrue();
            List<String> elements = (List<String>) result.getOutput().get("elements");
            assertThat(elements).hasSize(1);
        }

        @Test
        @SuppressWarnings("unchecked")
        void execute_extractBySelector_idSelector() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "extractBySelector");
            config.put("html", "<div id=\"main\">Main Content</div>");
            config.put("selector", "#main");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isTrue();
            List<String> elements = (List<String>) result.getOutput().get("elements");
            assertThat(elements).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Unknown Operation")
    class UnknownOperation {
        @Test
        void execute_unknownOperation_returnsFailure() {
            Map<String, Object> config = new HashMap<>();
            config.put("operation", "nonExistent");
            config.put("html", "<p>test</p>");

            NodeExecutionResult result = handler.execute(buildContext(config, null));

            assertThat(result.isSuccess()).isFalse();
        }
    }

    private NodeExecutionContext buildContext(Map<String, Object> config, Map<String, Object> inputData) {
        return NodeExecutionContext.builder()
                .executionId(UUID.randomUUID())
                .nodeId("html-1")
                .nodeType("html")
                .nodeConfig(new HashMap<>(config))
                .inputData(inputData)
                .userId(UUID.randomUUID())
                .flowId(UUID.randomUUID())
                .build();
    }
}
