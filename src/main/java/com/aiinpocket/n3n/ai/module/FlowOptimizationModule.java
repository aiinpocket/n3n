package com.aiinpocket.n3n.ai.module;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flow Optimization Module - Independent AI module for flow optimization.
 * Uses configured AI provider to analyze flows and suggest optimizations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlowOptimizationModule {

    public static final String FEATURE_NAME = "flowOptimization";

    private final SimpleAIProviderRegistry providerRegistry;
    private final ObjectMapper objectMapper;

    /**
     * Analyze a flow and return optimization suggestions
     */
    public OptimizationResult analyzeFlow(Map<String, Object> definition, UUID userId) {
        SimpleAIProvider provider = providerRegistry.getProviderForFeature(FEATURE_NAME, userId);

        if (!provider.isAvailable()) {
            log.warn("AI provider {} not available for flow optimization", provider.getName());
            return OptimizationResult.unavailable();
        }

        try {
            String flowJson = objectMapper.writeValueAsString(definition);
            String prompt = buildOptimizationPrompt(flowJson);
            String systemPrompt = getSystemPrompt();

            String response = provider.chat(prompt, systemPrompt, 2048, 0.3);
            return parseOptimizationResponse(response);
        } catch (Exception e) {
            log.error("Flow optimization failed", e);
            return OptimizationResult.error(e.getMessage());
        }
    }

    private String getSystemPrompt() {
        return """
            You are a workflow optimization expert. Analyze workflows and provide optimization suggestions.
            Always respond with valid JSON only, no additional text.
            """;
    }

    private String buildOptimizationPrompt(String flowJson) {
        return """
            Analyze the following workflow definition and provide optimization suggestions.

            Focus on:
            1. **Parallel Execution**: Identify nodes that have no dependencies and can run in parallel
            2. **Merge Opportunities**: Identify sequential HTTP requests to the same endpoint that could be batched
            3. **Redundant Nodes**: Identify unused or redundant nodes
            4. **Execution Order**: Suggest better execution order for efficiency

            Workflow Definition (JSON):
            ```json
            %s
            ```

            Respond ONLY with this JSON format:
            ```json
            {
              "suggestions": [
                {
                  "type": "parallel|merge|remove|reorder",
                  "title": "Short title in Traditional Chinese",
                  "description": "Detailed explanation in Traditional Chinese",
                  "affectedNodes": ["node1", "node2"],
                  "priority": 1
                }
              ]
            }
            ```

            Priority: 1 = high impact, 2 = medium, 3 = low
            If no optimizations needed, return {"suggestions": []}
            """.formatted(flowJson);
    }

    private OptimizationResult parseOptimizationResponse(String content) {
        try {
            String json = extractJson(content);
            JsonNode root = objectMapper.readTree(json);

            List<OptimizationSuggestion> suggestions = new ArrayList<>();
            JsonNode suggestionsNode = root.get("suggestions");

            if (suggestionsNode != null && suggestionsNode.isArray()) {
                for (JsonNode node : suggestionsNode) {
                    OptimizationSuggestion suggestion = new OptimizationSuggestion(
                        UUID.randomUUID().toString(),
                        node.has("type") ? node.get("type").asText() : "unknown",
                        node.has("title") ? node.get("title").asText() : "",
                        node.has("description") ? node.get("description").asText() : "",
                        extractStringList(node.get("affectedNodes")),
                        node.has("priority") ? node.get("priority").asInt() : 3
                    );
                    suggestions.add(suggestion);
                }
            }

            return OptimizationResult.success(suggestions);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse optimization response: {}", content);
            return OptimizationResult.error("Failed to parse AI response");
        }
    }

    private String extractJson(String content) {
        Pattern pattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)```");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
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

    // Inner classes for result types
    public record OptimizationSuggestion(
        String id,
        String type,
        String title,
        String description,
        List<String> affectedNodes,
        int priority
    ) {}

    public record OptimizationResult(
        boolean success,
        boolean available,
        List<OptimizationSuggestion> suggestions,
        String error
    ) {
        public static OptimizationResult success(List<OptimizationSuggestion> suggestions) {
            return new OptimizationResult(true, true, suggestions, null);
        }

        public static OptimizationResult error(String message) {
            return new OptimizationResult(false, true, List.of(), message);
        }

        public static OptimizationResult unavailable() {
            return new OptimizationResult(true, false, List.of(), "AI service unavailable");
        }
    }
}
