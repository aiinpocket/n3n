package com.aiinpocket.n3n.skill.builtin.http;

import com.aiinpocket.n3n.skill.BuiltinSkill;
import com.aiinpocket.n3n.skill.SkillResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * General-purpose HTTP API request skill.
 * Supports GET, POST, PUT, PATCH, DELETE methods.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiRequestSkill implements BuiltinSkill {

    private final WebClient.Builder webClientBuilder;

    @Override
    public String getName() {
        return "api_request";
    }

    @Override
    public String getDisplayName() {
        return "API Request";
    }

    @Override
    public String getDescription() {
        return "Make an HTTP API request with customizable method, headers, and body";
    }

    @Override
    public String getCategory() {
        return "http";
    }

    @Override
    public String getIcon() {
        return "api";
    }

    @Override
    public Map<String, Object> getInputSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "url", Map.of(
                    "type", "string",
                    "description", "The API endpoint URL"
                ),
                "method", Map.of(
                    "type", "string",
                    "enum", List.of("GET", "POST", "PUT", "PATCH", "DELETE"),
                    "default", "GET",
                    "description", "HTTP method"
                ),
                "headers", Map.of(
                    "type", "object",
                    "description", "HTTP headers",
                    "additionalProperties", Map.of("type", "string")
                ),
                "body", Map.of(
                    "type", "object",
                    "description", "Request body (for POST/PUT/PATCH)"
                ),
                "queryParams", Map.of(
                    "type", "object",
                    "description", "Query parameters",
                    "additionalProperties", Map.of("type", "string")
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
                "data", Map.of("type", "object", "description", "Response body"),
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

        String method = (String) input.getOrDefault("method", "GET");

        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) input.get("headers");

        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) input.get("body");

        @SuppressWarnings("unchecked")
        Map<String, String> queryParams = (Map<String, String>) input.get("queryParams");

        try {
            WebClient webClient = webClientBuilder.build();

            // Build URL with query params
            String finalUrl = url;
            if (queryParams != null && !queryParams.isEmpty()) {
                StringBuilder sb = new StringBuilder(url);
                sb.append(url.contains("?") ? "&" : "?");
                queryParams.forEach((k, v) -> sb.append(k).append("=").append(v).append("&"));
                finalUrl = sb.substring(0, sb.length() - 1);
            }

            HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());

            WebClient.RequestBodySpec requestSpec = webClient
                .method(httpMethod)
                .uri(finalUrl);

            // Add headers
            if (headers != null) {
                headers.forEach(requestSpec::header);
            }

            // Add body for methods that support it
            Mono<Map> responseMono;
            if (body != null && (httpMethod == HttpMethod.POST || httpMethod == HttpMethod.PUT || httpMethod == HttpMethod.PATCH)) {
                responseMono = requestSpec
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class);
            } else {
                responseMono = requestSpec
                    .retrieve()
                    .bodyToMono(Map.class);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> responseData = responseMono.block();

            return SkillResult.success(Map.of(
                "data", responseData != null ? responseData : Map.of(),
                "statusCode", 200
            ));

        } catch (Exception e) {
            log.error("API request to {} failed: {}", url, e.getMessage());
            return SkillResult.failure("API_ERROR", "API request failed: " + e.getMessage());
        }
    }
}
