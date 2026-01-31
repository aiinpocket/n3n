package com.aiinpocket.n3n.skill.builtin.web;

import com.aiinpocket.n3n.skill.BuiltinSkill;
import com.aiinpocket.n3n.skill.SkillResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Skill to fetch content from a URL.
 * Pure HTTP request - no AI involved.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FetchUrlSkill implements BuiltinSkill {

    private final WebClient.Builder webClientBuilder;

    @Override
    public String getName() {
        return "fetch_url";
    }

    @Override
    public String getDisplayName() {
        return "Fetch URL";
    }

    @Override
    public String getDescription() {
        return "Fetch content from a URL using HTTP GET request";
    }

    @Override
    public String getCategory() {
        return "web";
    }

    @Override
    public String getIcon() {
        return "global";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "url", Map.of(
                    "type", "string",
                    "description", "The URL to fetch"
                ),
                "headers", Map.of(
                    "type", "object",
                    "description", "Optional HTTP headers",
                    "additionalProperties", Map.of("type", "string")
                ),
                "timeout", Map.of(
                    "type", "integer",
                    "description", "Request timeout in milliseconds",
                    "default", 30000
                )
            ),
            "required", List.of("url")
        );
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "content", Map.of("type", "string", "description", "Response body"),
                "statusCode", Map.of("type", "integer", "description", "HTTP status code"),
                "headers", Map.of("type", "object", "description", "Response headers")
            )
        );
    }

    @Override
    public SkillResult execute(Map<String, Object> input) {
        String url = (String) input.get("url");
        if (url == null || url.isBlank()) {
            return SkillResult.failure("MISSING_URL", "URL is required");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, String> headers = (Map<String, String>) input.get("headers");

            WebClient webClient = webClientBuilder.build();
            WebClient.RequestHeadersSpec<?> request = webClient.get().uri(url);

            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    request = request.header(header.getKey(), header.getValue());
                }
            }

            String content = request
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return SkillResult.success(Map.of(
                "content", content != null ? content : "",
                "statusCode", 200
            ));

        } catch (Exception e) {
            log.error("Failed to fetch URL {}: {}", url, e.getMessage());
            return SkillResult.failure("FETCH_ERROR", "Failed to fetch URL: " + e.getMessage());
        }
    }
}
