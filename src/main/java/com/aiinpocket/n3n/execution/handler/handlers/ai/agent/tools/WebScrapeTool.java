package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Web scraping tool
 *
 * Allows AI Agent to scrape content from web pages, supports:
 * - Extracting full page text
 * - Extracting specific elements using CSS selectors
 * - Extracting links and images
 * - Extracting table data
 */
@Component
@Slf4j
public class WebScrapeTool implements AgentNodeTool {

    @Value("${n3n.tools.webscrape.timeout:10000}")
    private int timeout;

    @Value("${n3n.tools.webscrape.max-body-size:1048576}")
    private int maxBodySize; // 1MB default

    @Value("${n3n.tools.webscrape.user-agent:N3N-Bot/1.0 (AI Agent)}")
    private String userAgent;

    // Blocked domains (security consideration)
    private static final List<String> BLOCKED_DOMAINS = List.of(
            "localhost", "127.0.0.1", "0.0.0.0", "::1",
            "169.254.", "10.", "172.16.", "172.17.", "172.18.", "172.19.",
            "172.20.", "172.21.", "172.22.", "172.23.", "172.24.", "172.25.",
            "172.26.", "172.27.", "172.28.", "172.29.", "172.30.", "172.31.",
            "192.168.", "metadata.google", "169.254.169.254"
    );

    @Override
    public String getId() {
        return "web_scrape";
    }

    @Override
    public String getName() {
        return "Web Scrape";
    }

    @Override
    public String getDescription() {
        return """
                Web page content scraping. Supported operations:
                - text: Extract page plain text content
                - html: Extract page HTML
                - select: Extract specific elements using CSS selectors
                - links: Extract all links
                - images: Extract all image URLs
                - table: Extract table data
                - metadata: Extract page title, description, and other metadata

                Parameters:
                - url: Web page URL to scrape
                - operation: Operation type (default text)
                - selector: CSS selector (only for select operation)
                - limit: Maximum number of items to extract (for links, images)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "url", Map.of(
                                "type", "string",
                                "description", "Web page URL to scrape"
                        ),
                        "operation", Map.of(
                                "type", "string",
                                "enum", List.of("text", "html", "select", "links", "images", "table", "metadata"),
                                "description", "Operation type",
                                "default", "text"
                        ),
                        "selector", Map.of(
                                "type", "string",
                                "description", "CSS selector (for select operation)"
                        ),
                        "limit", Map.of(
                                "type", "integer",
                                "description", "Maximum number of items to extract",
                                "default", 50
                        )
                ),
                "required", List.of("url")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = (String) parameters.get("url");
                if (url == null || url.isBlank()) {
                    return ToolResult.failure("URL cannot be empty");
                }

                // Validate URL
                if (!isValidUrl(url)) {
                    return ToolResult.failure("Invalid URL or access denied");
                }

                String operation = (String) parameters.getOrDefault("operation", "text");
                String selector = (String) parameters.get("selector");
                int limit = parameters.containsKey("limit")
                        ? ((Number) parameters.get("limit")).intValue()
                        : 50;

                log.debug("Web scrape: url={}, operation={}", url, operation);

                // Fetch web page
                // Security: disable auto-redirect to prevent SSRF bypass
                Document doc = Jsoup.connect(url)
                        .userAgent(userAgent)
                        .timeout(timeout)
                        .maxBodySize(maxBodySize)
                        .followRedirects(false)  // Disable redirects to prevent SSRF bypass
                        .get();

                // Execute operation
                return switch (operation.toLowerCase()) {
                    case "text" -> extractText(doc, url);
                    case "html" -> extractHtml(doc, url);
                    case "select" -> extractBySelector(doc, url, selector, limit);
                    case "links" -> extractLinks(doc, url, limit);
                    case "images" -> extractImages(doc, url, limit);
                    case "table" -> extractTables(doc, url, limit);
                    case "metadata" -> extractMetadata(doc, url);
                    default -> ToolResult.failure("Unsupported operation: " + operation);
                };

            } catch (IOException e) {
                log.error("Web scrape failed", e);
                return ToolResult.failure("Web scraping failed");
            } catch (Exception e) {
                log.error("Web scrape error", e);
                return ToolResult.failure("Scraping error");
            }
        });
    }

    /**
     * Validate whether URL is safe
     * Security: use IP address resolution to prevent DNS rebinding attacks
     */
    private boolean isValidUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            String scheme = uri.getScheme();

            // Only allow HTTP/HTTPS
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                log.warn("Blocked non-HTTP(S) scheme: {}", scheme);
                return false;
            }

            if (host == null || host.isBlank()) {
                return false;
            }

            // Check blocked domains (string matching)
            String lowerHost = host.toLowerCase();
            for (String blocked : BLOCKED_DOMAINS) {
                if (lowerHost.equals(blocked) || lowerHost.startsWith(blocked) ||
                    lowerHost.contains(blocked)) {
                    log.warn("Blocked URL access to domain: {}", host);
                    return false;
                }
            }

            // Resolve IP address for stricter checks (prevent DNS rebinding)
            try {
                InetAddress[] addresses = InetAddress.getAllByName(host);
                for (InetAddress addr : addresses) {
                    if (isPrivateOrReservedIP(addr)) {
                        log.warn("Blocked URL resolving to private/reserved IP: {} -> {}", host, addr.getHostAddress());
                        return false;
                    }
                }
            } catch (UnknownHostException e) {
                log.warn("Cannot resolve host: {}", host);
                return false;
            }

            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Check if IP address is private or reserved
     * Includes all RFC 1918 private ranges and other reserved ranges
     */
    private boolean isPrivateOrReservedIP(InetAddress addr) {
        byte[] ip = addr.getAddress();

        // IPv4 check
        if (ip.length == 4) {
            int b0 = ip[0] & 0xFF;
            int b1 = ip[1] & 0xFF;

            // 127.0.0.0/8 (loopback)
            if (b0 == 127) return true;

            // 10.0.0.0/8 (private)
            if (b0 == 10) return true;

            // 172.16.0.0/12 (private) - 172.16.0.0 to 172.31.255.255
            if (b0 == 172 && b1 >= 16 && b1 <= 31) return true;

            // 192.168.0.0/16 (private)
            if (b0 == 192 && b1 == 168) return true;

            // 169.254.0.0/16 (link-local, cloud metadata)
            if (b0 == 169 && b1 == 254) return true;

            // 0.0.0.0/8 (current network)
            if (b0 == 0) return true;

            // 100.64.0.0/10 (carrier-grade NAT)
            if (b0 == 100 && b1 >= 64 && b1 <= 127) return true;

            // 192.0.0.0/24 (IETF Protocol Assignments)
            if (b0 == 192 && b1 == 0 && (ip[2] & 0xFF) == 0) return true;

            // 192.0.2.0/24 (TEST-NET-1)
            if (b0 == 192 && b1 == 0 && (ip[2] & 0xFF) == 2) return true;

            // 198.51.100.0/24 (TEST-NET-2)
            if (b0 == 198 && b1 == 51 && (ip[2] & 0xFF) == 100) return true;

            // 203.0.113.0/24 (TEST-NET-3)
            if (b0 == 203 && b1 == 0 && (ip[2] & 0xFF) == 113) return true;

            // 224.0.0.0/4 (multicast)
            if (b0 >= 224 && b0 <= 239) return true;

            // 240.0.0.0/4 (reserved)
            if (b0 >= 240) return true;
        }

        // IPv6 check
        if (ip.length == 16) {
            // ::1 (loopback)
            if (addr.isLoopbackAddress()) return true;

            // fe80::/10 (link-local)
            if (addr.isLinkLocalAddress()) return true;

            // fc00::/7 (unique local)
            int b0 = ip[0] & 0xFF;
            if (b0 == 0xFC || b0 == 0xFD) return true;
        }

        return addr.isLoopbackAddress() || addr.isSiteLocalAddress() ||
               addr.isLinkLocalAddress() || addr.isAnyLocalAddress();
    }

    /**
     * Extract plain text
     */
    private ToolResult extractText(Document doc, String url) {
        String text = doc.body().text();
        // Limit length
        if (text.length() > 10000) {
            text = text.substring(0, 10000) + "...(truncated)";
        }

        return ToolResult.success(
                String.format("Text content extracted from %s:\n\n%s", url, text),
                Map.of(
                        "url", url,
                        "title", doc.title(),
                        "text_length", text.length()
                )
        );
    }

    /**
     * Extract HTML
     */
    private ToolResult extractHtml(Document doc, String url) {
        String html = doc.html();
        if (html.length() > 50000) {
            html = html.substring(0, 50000) + "...(truncated)";
        }

        return ToolResult.success(
                String.format("HTML extracted from %s (first 50000 characters)", url),
                Map.of(
                        "url", url,
                        "html", html,
                        "html_length", html.length()
                )
        );
    }

    /**
     * Extract using CSS selector
     */
    private ToolResult extractBySelector(Document doc, String url, String selector, int limit) {
        if (selector == null || selector.isBlank()) {
            return ToolResult.failure("The select operation requires a selector parameter");
        }

        Elements elements = doc.select(selector);
        List<Map<String, String>> results = new ArrayList<>();

        int count = 0;
        for (Element el : elements) {
            if (count >= limit) break;

            Map<String, String> item = new HashMap<>();
            item.put("tag", el.tagName());
            item.put("text", el.text());
            item.put("html", el.outerHtml().length() > 500 ?
                    el.outerHtml().substring(0, 500) + "..." : el.outerHtml());

            // Common attributes
            if (el.hasAttr("href")) item.put("href", el.attr("abs:href"));
            if (el.hasAttr("src")) item.put("src", el.attr("abs:src"));
            if (el.hasAttr("class")) item.put("class", el.attr("class"));
            if (el.hasAttr("id")) item.put("id", el.attr("id"));

            results.add(item);
            count++;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Found %d elements using selector '%s' from %s (showing first %d):\n\n",
                elements.size(), selector, url, results.size()));

        for (int i = 0; i < results.size(); i++) {
            Map<String, String> item = results.get(i);
            sb.append(String.format("%d. <%s>: %s\n", i + 1, item.get("tag"), item.get("text")));
        }

        return ToolResult.success(sb.toString(), Map.of(
                "url", url,
                "selector", selector,
                "total_found", elements.size(),
                "elements", results
        ));
    }

    /**
     * Extract links
     */
    private ToolResult extractLinks(Document doc, String url, int limit) {
        Elements links = doc.select("a[href]");
        List<Map<String, String>> results = new ArrayList<>();

        int count = 0;
        for (Element link : links) {
            if (count >= limit) break;

            String href = link.attr("abs:href");
            String text = link.text().trim();

            if (!href.isBlank()) {
                results.add(Map.of(
                        "text", text.isBlank() ? "(no text)" : text,
                        "href", href
                ));
                count++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Extracted %d links from %s (total %d):\n\n",
                results.size(), url, links.size()));

        for (int i = 0; i < results.size(); i++) {
            Map<String, String> item = results.get(i);
            sb.append(String.format("%d. %s\n   %s\n", i + 1, item.get("text"), item.get("href")));
        }

        return ToolResult.success(sb.toString(), Map.of(
                "url", url,
                "total_links", links.size(),
                "links", results
        ));
    }

    /**
     * Extract images
     */
    private ToolResult extractImages(Document doc, String url, int limit) {
        Elements images = doc.select("img[src]");
        List<Map<String, String>> results = new ArrayList<>();

        int count = 0;
        for (Element img : images) {
            if (count >= limit) break;

            String src = img.attr("abs:src");
            String alt = img.attr("alt");

            if (!src.isBlank()) {
                results.add(Map.of(
                        "src", src,
                        "alt", alt.isBlank() ? "(no alt text)" : alt
                ));
                count++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Extracted %d images from %s (total %d):\n\n",
                results.size(), url, images.size()));

        for (int i = 0; i < results.size(); i++) {
            Map<String, String> item = results.get(i);
            sb.append(String.format("%d. %s\n   Alt: %s\n", i + 1, item.get("src"), item.get("alt")));
        }

        return ToolResult.success(sb.toString(), Map.of(
                "url", url,
                "total_images", images.size(),
                "images", results
        ));
    }

    /**
     * Extract tables
     */
    private ToolResult extractTables(Document doc, String url, int limit) {
        Elements tables = doc.select("table");
        List<Map<String, Object>> results = new ArrayList<>();

        int tableCount = 0;
        for (Element table : tables) {
            if (tableCount >= limit) break;

            List<List<String>> rows = new ArrayList<>();
            Elements trs = table.select("tr");

            for (Element tr : trs) {
                List<String> row = new ArrayList<>();
                Elements cells = tr.select("th, td");
                for (Element cell : cells) {
                    row.add(cell.text().trim());
                }
                if (!row.isEmpty()) {
                    rows.add(row);
                }
            }

            if (!rows.isEmpty()) {
                results.add(Map.of(
                        "table_index", tableCount + 1,
                        "rows", rows.size(),
                        "columns", rows.isEmpty() ? 0 : rows.get(0).size(),
                        "data", rows
                ));
                tableCount++;
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Extracted %d tables from %s (total %d):\n\n",
                results.size(), url, tables.size()));

        for (Map<String, Object> table : results) {
            sb.append(String.format("Table %d (%d rows x %d columns):\n",
                    table.get("table_index"), table.get("rows"), table.get("columns")));

            @SuppressWarnings("unchecked")
            List<List<String>> data = (List<List<String>>) table.get("data");
            for (int i = 0; i < Math.min(5, data.size()); i++) {
                sb.append("  ").append(String.join(" | ", data.get(i))).append("\n");
            }
            if (data.size() > 5) {
                sb.append("  ...(" + (data.size() - 5) + " more rows)\n");
            }
            sb.append("\n");
        }

        return ToolResult.success(sb.toString(), Map.of(
                "url", url,
                "total_tables", tables.size(),
                "tables", results
        ));
    }

    /**
     * Extract metadata
     */
    private ToolResult extractMetadata(Document doc, String url) {
        Map<String, String> metadata = new HashMap<>();

        metadata.put("title", doc.title());
        metadata.put("url", url);

        // Meta tags
        Elements metaTags = doc.select("meta");
        for (Element meta : metaTags) {
            String name = meta.attr("name");
            String property = meta.attr("property");
            String content = meta.attr("content");

            if (!name.isBlank() && !content.isBlank()) {
                metadata.put("meta:" + name, content);
            }
            if (!property.isBlank() && !content.isBlank()) {
                metadata.put("meta:" + property, content);
            }
        }

        // Canonical URL
        Element canonical = doc.selectFirst("link[rel=canonical]");
        if (canonical != null) {
            metadata.put("canonical", canonical.attr("href"));
        }

        // Language
        String lang = doc.select("html").attr("lang");
        if (!lang.isBlank()) {
            metadata.put("language", lang);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Metadata extracted from %s:\n\n", url));

        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            sb.append(String.format("- %s: %s\n", entry.getKey(), entry.getValue()));
        }

        return ToolResult.success(sb.toString(), Map.of(
                "url", url,
                "metadata", metadata
        ));
    }

    @Override
    public String getCategory() {
        return "web";
    }
}
