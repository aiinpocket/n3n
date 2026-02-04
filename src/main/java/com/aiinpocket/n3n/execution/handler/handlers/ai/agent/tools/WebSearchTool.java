package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 網路搜索工具
 * 讓 AI Agent 能夠搜索網路資訊
 *
 * 支援多個搜索引擎 API（可配置）
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebSearchTool implements AgentNodeTool {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    @Value("${n3n.search.api-key:}")
    private String searchApiKey;

    @Value("${n3n.search.engine:duckduckgo}")
    private String searchEngine;

    @Override
    public String getId() {
        return "web_search";
    }

    @Override
    public String getName() {
        return "Web Search";
    }

    @Override
    public String getDescription() {
        return "Search the web for information. Returns relevant search results with titles, snippets, and URLs. " +
               "Use this to find current information, research topics, or verify facts.";
    }

    @Override
    public String getCategory() {
        return "search";
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "query", Map.of(
                    "type", "string",
                    "description", "Search query"
                ),
                "maxResults", Map.of(
                    "type", "integer",
                    "description", "Maximum number of results to return",
                    "default", 5
                )
            ),
            "required", List.of("query")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            String query = (String) parameters.get("query");
            int maxResults = ((Number) parameters.getOrDefault("maxResults", 5)).intValue();

            try {
                List<SearchResult> results = performSearch(query, maxResults);

                if (results.isEmpty()) {
                    return ToolResult.success("No results found for: " + query);
                }

                StringBuilder output = new StringBuilder();
                output.append("Search results for: ").append(query).append("\n\n");

                List<Map<String, Object>> resultMaps = new ArrayList<>();
                for (int i = 0; i < results.size(); i++) {
                    SearchResult r = results.get(i);
                    output.append(i + 1).append(". ").append(r.title()).append("\n");
                    output.append("   URL: ").append(r.url()).append("\n");
                    output.append("   ").append(r.snippet()).append("\n\n");

                    resultMaps.add(Map.of(
                        "title", r.title(),
                        "url", r.url(),
                        "snippet", r.snippet()
                    ));
                }

                log.debug("Web search completed: {} -> {} results", query, results.size());

                return ToolResult.success(output.toString(), Map.of("results", resultMaps));

            } catch (Exception e) {
                log.error("Web search failed: {}", e.getMessage());
                return ToolResult.failure("Web search failed: " + e.getMessage());
            }
        });
    }

    private List<SearchResult> performSearch(String query, int maxResults) throws Exception {
        // 使用 DuckDuckGo Instant Answer API（免費，不需要 API key）
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://api.duckduckgo.com/?q=" + encodedQuery + "&format=json&no_html=1&skip_disambig=1";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(10))
            .header("User-Agent", "N3N-Agent/1.0")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Search API returned status " + response.statusCode());
        }

        JsonNode json = objectMapper.readTree(response.body());
        List<SearchResult> results = new ArrayList<>();

        // 嘗試獲取 Abstract（最相關的結果）
        String abstractText = json.path("AbstractText").asText("");
        String abstractUrl = json.path("AbstractURL").asText("");
        String abstractSource = json.path("AbstractSource").asText("");
        if (!abstractText.isEmpty() && !abstractUrl.isEmpty()) {
            results.add(new SearchResult(
                abstractSource.isEmpty() ? "Main Result" : abstractSource,
                abstractUrl,
                abstractText
            ));
        }

        // 嘗試獲取 RelatedTopics
        JsonNode topics = json.path("RelatedTopics");
        if (topics.isArray()) {
            for (JsonNode topic : topics) {
                if (results.size() >= maxResults) break;

                String text = topic.path("Text").asText("");
                String topicUrl = topic.path("FirstURL").asText("");

                if (!text.isEmpty() && !topicUrl.isEmpty()) {
                    // 從 URL 提取標題
                    String title = extractTitleFromText(text);
                    results.add(new SearchResult(title, topicUrl, text));
                }
            }
        }

        // 如果沒有結果，嘗試 Infobox
        if (results.isEmpty()) {
            JsonNode infobox = json.path("Infobox");
            if (infobox.isObject()) {
                String heading = json.path("Heading").asText("Result");
                String infoUrl = json.path("AbstractURL").asText("");
                JsonNode content = infobox.path("content");
                if (content.isArray() && content.size() > 0) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode item : content) {
                        String label = item.path("label").asText("");
                        String value = item.path("value").asText("");
                        if (!label.isEmpty() && !value.isEmpty()) {
                            sb.append(label).append(": ").append(value).append(". ");
                        }
                    }
                    if (sb.length() > 0) {
                        results.add(new SearchResult(heading, infoUrl, sb.toString().trim()));
                    }
                }
            }
        }

        return results;
    }

    private String extractTitleFromText(String text) {
        // DuckDuckGo 的 Text 格式通常是 "Title - Description"
        int dashIndex = text.indexOf(" - ");
        if (dashIndex > 0 && dashIndex < 100) {
            return text.substring(0, dashIndex);
        }
        // 否則取前 50 個字元
        if (text.length() > 50) {
            return text.substring(0, 50) + "...";
        }
        return text;
    }

    private record SearchResult(String title, String url, String snippet) {}
}
