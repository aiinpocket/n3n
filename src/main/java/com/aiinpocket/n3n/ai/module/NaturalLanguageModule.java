package com.aiinpocket.n3n.ai.module;

import com.aiinpocket.n3n.execution.handler.NodeHandlerInfo;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Natural Language Module - Independent AI module for NL to flow generation.
 * Uses configured AI provider (can be different from flow optimization).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NaturalLanguageModule {

    public static final String FEATURE_NAME = "naturalLanguage";

    private final SimpleAIProviderRegistry providerRegistry;
    private final NodeHandlerRegistry nodeHandlerRegistry;
    private final ObjectMapper objectMapper;

    /**
     * Generate a flow from natural language description
     */
    public FlowGenerationResult generateFlow(String userInput, UUID userId, Set<String> installedNodeTypes) {
        SimpleAIProvider provider = providerRegistry.getProviderForFeature(FEATURE_NAME, userId);

        if (!provider.isAvailable()) {
            log.warn("AI provider {} not available for flow generation", provider.getName());
            return FlowGenerationResult.unavailable();
        }

        try {
            // Get available node types
            List<NodeHandlerInfo> availableNodes = nodeHandlerRegistry.listHandlerInfo();
            String nodesContext = buildNodesContext(availableNodes, installedNodeTypes);

            String prompt = buildFlowGenerationPrompt(userInput, nodesContext);
            String systemPrompt = getFlowGenerationSystemPrompt();

            String response = provider.chat(prompt, systemPrompt, 4096, 0.7);
            return parseFlowGenerationResponse(response, availableNodes, installedNodeTypes);
        } catch (Exception e) {
            log.error("Flow generation failed", e);
            return FlowGenerationResult.error(e.getMessage());
        }
    }

    /**
     * Recommend nodes based on current flow context
     */
    public NodeRecommendationResult recommendNodes(
            Map<String, Object> currentFlow,
            String searchQuery,
            UUID userId,
            Set<String> installedNodeTypes) {

        SimpleAIProvider provider = providerRegistry.getProviderForFeature(FEATURE_NAME, userId);

        if (!provider.isAvailable()) {
            return NodeRecommendationResult.unavailable();
        }

        try {
            List<NodeHandlerInfo> availableNodes = nodeHandlerRegistry.listHandlerInfo();

            // Extract current flow context
            Set<String> usedCategories = extractCategories(currentFlow, availableNodes);
            String context = buildRecommendationContext(currentFlow, usedCategories, installedNodeTypes);

            String prompt = buildNodeRecommendationPrompt(context, searchQuery, availableNodes, installedNodeTypes);
            String systemPrompt = getNodeRecommendationSystemPrompt();

            String response = provider.chat(prompt, systemPrompt, 2048, 0.5);
            return parseNodeRecommendationResponse(response, installedNodeTypes);
        } catch (Exception e) {
            log.error("Node recommendation failed", e);
            return NodeRecommendationResult.error(e.getMessage());
        }
    }

    private String buildNodesContext(List<NodeHandlerInfo> nodes, Set<String> installed) {
        Map<String, List<NodeHandlerInfo>> byCategory = nodes.stream()
            .collect(Collectors.groupingBy(n -> n.getCategory() != null ? n.getCategory() : "other"));

        StringBuilder sb = new StringBuilder("Available node types by category:\n");
        for (Map.Entry<String, List<NodeHandlerInfo>> entry : byCategory.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ");
            sb.append(entry.getValue().stream()
                .map(n -> n.getType() + (installed.contains(n.getType()) ? "*" : ""))
                .collect(Collectors.joining(", ")));
            sb.append("\n");
        }
        sb.append("(* = already installed/available)\n");
        return sb.toString();
    }

    private String getFlowGenerationSystemPrompt() {
        return """
            You are a workflow automation expert. Generate workflow definitions from user descriptions.
            Always respond with valid JSON only.
            Use Traditional Chinese for display names and descriptions.
            """;
    }

    private String buildFlowGenerationPrompt(String userInput, String nodesContext) {
        return """
            User request: %s

            %s

            Generate a workflow definition that fulfills the user's request.
            Respond ONLY with this JSON format:
            ```json
            {
              "understanding": "Brief summary of what you understood (in Traditional Chinese)",
              "nodes": [
                {
                  "id": "node_1",
                  "type": "trigger|scheduleTrigger|httpRequest|code|condition|...",
                  "label": "Node display name in Traditional Chinese",
                  "config": {}
                }
              ],
              "edges": [
                {"source": "node_1", "target": "node_2"}
              ],
              "requiredNodes": ["nodeType1", "nodeType2"],
              "missingNodes": ["nodeType that is not in available list"]
            }
            ```
            """.formatted(userInput, nodesContext);
    }

    private FlowGenerationResult parseFlowGenerationResponse(
            String content,
            List<NodeHandlerInfo> availableNodes,
            Set<String> installedNodeTypes) {
        try {
            String json = extractJson(content);
            JsonNode root = objectMapper.readTree(json);

            String understanding = root.has("understanding") ? root.get("understanding").asText() : "";

            // Parse nodes
            List<Map<String, Object>> nodes = new ArrayList<>();
            JsonNode nodesNode = root.get("nodes");
            if (nodesNode != null && nodesNode.isArray()) {
                for (JsonNode n : nodesNode) {
                    Map<String, Object> node = new HashMap<>();
                    node.put("id", n.get("id").asText());
                    node.put("type", n.get("type").asText());
                    node.put("label", n.has("label") ? n.get("label").asText() : n.get("type").asText());
                    if (n.has("config")) {
                        node.put("config", objectMapper.convertValue(n.get("config"), Map.class));
                    }
                    nodes.add(node);
                }
            }

            // Parse edges
            List<Map<String, String>> edges = new ArrayList<>();
            JsonNode edgesNode = root.get("edges");
            if (edgesNode != null && edgesNode.isArray()) {
                for (JsonNode e : edgesNode) {
                    edges.add(Map.of(
                        "source", e.get("source").asText(),
                        "target", e.get("target").asText()
                    ));
                }
            }

            // Identify missing nodes
            Set<String> availableTypes = availableNodes.stream()
                .map(NodeHandlerInfo::getType)
                .collect(Collectors.toSet());
            List<String> requiredTypes = nodes.stream()
                .map(n -> (String) n.get("type"))
                .distinct()
                .toList();
            List<String> missingTypes = requiredTypes.stream()
                .filter(t -> !availableTypes.contains(t) && !installedNodeTypes.contains(t))
                .toList();

            return FlowGenerationResult.success(
                understanding,
                Map.of("nodes", nodes, "edges", edges),
                requiredTypes,
                missingTypes
            );
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse flow generation response: {}", content);
            return FlowGenerationResult.error("Failed to parse AI response");
        }
    }

    private Set<String> extractCategories(Map<String, Object> flow, List<NodeHandlerInfo> availableNodes) {
        Map<String, String> typeToCategory = availableNodes.stream()
            .collect(Collectors.toMap(
                NodeHandlerInfo::getType,
                n -> n.getCategory() != null ? n.getCategory() : "other",
                (a, b) -> a
            ));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> nodes = (List<Map<String, Object>>) flow.get("nodes");
        if (nodes == null) return Set.of();

        return nodes.stream()
            .map(n -> (String) n.get("type"))
            .filter(Objects::nonNull)
            .map(t -> typeToCategory.getOrDefault(t, "other"))
            .collect(Collectors.toSet());
    }

    private String buildRecommendationContext(
            Map<String, Object> flow,
            Set<String> usedCategories,
            Set<String> installedTypes) {
        StringBuilder sb = new StringBuilder();
        sb.append("Current flow categories: ").append(String.join(", ", usedCategories)).append("\n");
        sb.append("Installed nodes: ").append(String.join(", ", installedTypes)).append("\n");
        return sb.toString();
    }

    private String getNodeRecommendationSystemPrompt() {
        return """
            You are a workflow automation expert. Recommend useful nodes based on context.
            Always respond with valid JSON only.
            Use Traditional Chinese for descriptions and reasons.
            """;
    }

    private String buildNodeRecommendationPrompt(
            String context,
            String searchQuery,
            List<NodeHandlerInfo> availableNodes,
            Set<String> installedTypes) {

        String nodesList = availableNodes.stream()
            .filter(n -> !installedTypes.contains(n.getType()))
            .map(n -> String.format("- %s (%s): %s",
                n.getType(),
                n.getCategory(),
                n.getDescription()))
            .collect(Collectors.joining("\n"));

        return """
            %s

            User search query: %s

            Available nodes NOT yet installed:
            %s

            Recommend 3-5 nodes that would be useful. For each recommendation, explain why.
            Respond ONLY with this JSON format:
            ```json
            {
              "recommendations": [
                {
                  "nodeType": "httpRequest",
                  "displayName": "HTTP 請求",
                  "reason": "Why this node is useful (in Traditional Chinese)",
                  "pros": ["優點1", "優點2"],
                  "cons": ["注意事項1"]
                }
              ]
            }
            ```
            """.formatted(context, searchQuery != null ? searchQuery : "(無特定搜尋)", nodesList);
    }

    private NodeRecommendationResult parseNodeRecommendationResponse(String content, Set<String> installedTypes) {
        try {
            String json = extractJson(content);
            JsonNode root = objectMapper.readTree(json);

            List<NodeRecommendation> recommendations = new ArrayList<>();
            JsonNode recsNode = root.get("recommendations");

            if (recsNode != null && recsNode.isArray()) {
                for (JsonNode n : recsNode) {
                    String nodeType = n.get("nodeType").asText();
                    // Skip if already installed
                    if (installedTypes.contains(nodeType)) continue;

                    recommendations.add(new NodeRecommendation(
                        nodeType,
                        n.has("displayName") ? n.get("displayName").asText() : nodeType,
                        n.has("reason") ? n.get("reason").asText() : "",
                        extractStringList(n.get("pros")),
                        extractStringList(n.get("cons")),
                        !installedTypes.contains(nodeType)
                    ));
                }
            }

            return NodeRecommendationResult.success(recommendations);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse node recommendation response: {}", content);
            return NodeRecommendationResult.error("Failed to parse AI response");
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

    // Result records
    public record FlowGenerationResult(
        boolean success,
        boolean available,
        String understanding,
        Map<String, Object> flowDefinition,
        List<String> requiredNodes,
        List<String> missingNodes,
        String error
    ) {
        public static FlowGenerationResult success(
                String understanding,
                Map<String, Object> definition,
                List<String> required,
                List<String> missing) {
            return new FlowGenerationResult(true, true, understanding, definition, required, missing, null);
        }

        public static FlowGenerationResult error(String message) {
            return new FlowGenerationResult(false, true, null, null, List.of(), List.of(), message);
        }

        public static FlowGenerationResult unavailable() {
            return new FlowGenerationResult(true, false, null, null, List.of(), List.of(), "AI service unavailable");
        }
    }

    public record NodeRecommendation(
        String nodeType,
        String displayName,
        String reason,
        List<String> pros,
        List<String> cons,
        boolean needsInstall
    ) {}

    public record NodeRecommendationResult(
        boolean success,
        boolean available,
        List<NodeRecommendation> recommendations,
        String error
    ) {
        public static NodeRecommendationResult success(List<NodeRecommendation> recommendations) {
            return new NodeRecommendationResult(true, true, recommendations, null);
        }

        public static NodeRecommendationResult error(String message) {
            return new NodeRecommendationResult(false, true, List.of(), message);
        }

        public static NodeRecommendationResult unavailable() {
            return new NodeRecommendationResult(true, false, List.of(), "AI service unavailable");
        }
    }
}
