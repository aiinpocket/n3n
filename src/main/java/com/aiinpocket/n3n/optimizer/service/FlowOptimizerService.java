package com.aiinpocket.n3n.optimizer.service;

import com.aiinpocket.n3n.optimizer.config.FlowOptimizerConfig;
import com.aiinpocket.n3n.optimizer.dto.FlowOptimizationResponse;
import com.aiinpocket.n3n.optimizer.dto.FlowOptimizationResponse.OptimizationSuggestion;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlowOptimizerService {

    private final FlowOptimizerConfig config;
    private final ObjectMapper objectMapper;

    private WebClient webClient;

    @PostConstruct
    public void init() {
        this.webClient = WebClient.builder()
            .baseUrl(config.getUrl())
            .build();
    }

    public FlowOptimizationResponse analyzeFlow(Map<String, Object> definition) {
        if (!config.isEnabled()) {
            log.debug("Flow optimizer is disabled");
            return FlowOptimizationResponse.disabled();
        }

        try {
            String flowJson = objectMapper.writeValueAsString(definition);
            String prompt = buildPrompt(flowJson);

            String response = callLlamafile(prompt);
            return parseResponse(response);

        } catch (WebClientResponseException e) {
            log.warn("Flow optimizer service unavailable: {}", e.getMessage());
            return FlowOptimizationResponse.error("Optimizer service unavailable");
        } catch (Exception e) {
            log.error("Error analyzing flow", e);
            return FlowOptimizationResponse.error(e.getMessage());
        }
    }

    private String buildPrompt(String flowJson) {
        return """
            You are a workflow optimization expert. Analyze the following workflow definition and provide optimization suggestions.

            Focus on:
            1. **Parallel Execution**: Identify nodes that have no dependencies on each other and can run in parallel
            2. **Merge Opportunities**: Identify sequential HTTP requests to the same endpoint that could be batched
            3. **Redundant Nodes**: Identify unused or redundant nodes
            4. **Execution Order**: Suggest better execution order for efficiency

            Workflow Definition (JSON):
            ```json
            %s
            ```

            Respond in the following JSON format ONLY (no additional text):
            ```json
            {
              "suggestions": [
                {
                  "type": "parallel|merge|remove|reorder",
                  "title": "Short title",
                  "description": "Detailed explanation",
                  "affectedNodes": ["node1", "node2"],
                  "priority": 1
                }
              ]
            }
            ```

            Priority: 1 = high impact, 2 = medium, 3 = low
            If no optimizations are needed, return {"suggestions": []}
            """.formatted(flowJson);
    }

    private String callLlamafile(String prompt) {
        Map<String, Object> request = Map.of(
            "model", config.getModel(),
            "messages", List.of(
                Map.of("role", "user", "content", prompt)
            ),
            "temperature", config.getTemperature(),
            "max_tokens", config.getMaxTokens()
        );

        Map<String, Object> response = webClient.post()
            .uri("/v1/chat/completions")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(Map.class)
            .timeout(Duration.ofMillis(config.getTimeoutMs()))
            .block();

        if (response != null && response.containsKey("choices")) {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (!choices.isEmpty()) {
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                return (String) message.get("content");
            }
        }

        throw new RuntimeException("Invalid response from optimizer service");
    }

    private FlowOptimizationResponse parseResponse(String content) {
        try {
            // Extract JSON from markdown code blocks if present
            String json = extractJson(content);
            JsonNode root = objectMapper.readTree(json);

            List<OptimizationSuggestion> suggestions = new ArrayList<>();
            JsonNode suggestionsNode = root.get("suggestions");

            if (suggestionsNode != null && suggestionsNode.isArray()) {
                for (JsonNode node : suggestionsNode) {
                    OptimizationSuggestion suggestion = OptimizationSuggestion.builder()
                        .type(node.has("type") ? node.get("type").asText() : "unknown")
                        .title(node.has("title") ? node.get("title").asText() : "")
                        .description(node.has("description") ? node.get("description").asText() : "")
                        .affectedNodes(extractStringList(node.get("affectedNodes")))
                        .priority(node.has("priority") ? node.get("priority").asInt() : 3)
                        .build();
                    suggestions.add(suggestion);
                }
            }

            return FlowOptimizationResponse.builder()
                .success(true)
                .suggestions(suggestions)
                .build();

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse optimizer response: {}", content);
            return FlowOptimizationResponse.error("Failed to parse optimization results");
        }
    }

    private String extractJson(String content) {
        // Try to extract JSON from markdown code blocks
        Pattern pattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // If no code block, assume the entire content is JSON
        return content.trim();
    }

    private List<String> extractStringList(JsonNode node) {
        List<String> result = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                result.add(item.asText());
            }
        }
        return result;
    }

    public boolean isServiceAvailable() {
        if (!config.isEnabled()) {
            return false;
        }

        try {
            webClient.get()
                .uri("/health")
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(5))
                .block();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
