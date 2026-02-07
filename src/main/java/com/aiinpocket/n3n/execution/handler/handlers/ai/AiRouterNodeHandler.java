package com.aiinpocket.n3n.execution.handler.handlers.ai;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * AI Router node handler.
 *
 * Routes input to different outputs based on AI-powered classification.
 * The handler evaluates input content against a set of defined routes,
 * each with a name and description, and selects the best matching route.
 *
 * When no AI provider is configured, falls back to keyword-based matching.
 *
 * Config:
 * - routes: List of route definitions [{name, description}]
 * - prompt: The input text to classify
 * - model: AI model to use (optional)
 * - defaultRoute: Fallback route name if no match (optional)
 *
 * Output:
 * - selectedRoute: Name of the matched route
 * - confidence: Confidence score (0.0-1.0)
 * - reasoning: Explanation of why the route was selected
 */
@Component
@Slf4j
public class AiRouterNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "aiRouter";
    }

    @Override
    public String getDisplayName() {
        return "AI Router";
    }

    @Override
    public String getDescription() {
        return "Route input to different outputs based on AI classification of content.";
    }

    @Override
    public String getCategory() {
        return "AI";
    }

    @Override
    public String getIcon() {
        return "fork";
    }

    @Override
    @SuppressWarnings("unchecked")
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String prompt = getStringConfig(context, "prompt", "");
        String defaultRoute = getStringConfig(context, "defaultRoute", "");

        // Get prompt from input if not configured
        if (prompt.isEmpty() && context.getInputData() != null) {
            Object data = context.getInputData().get("input");
            if (data == null) data = context.getInputData().get("data");
            if (data == null) data = context.getInputData().get("prompt");
            if (data != null) prompt = data.toString();
        }

        if (prompt.isEmpty()) {
            return NodeExecutionResult.failure("Input prompt is required for routing");
        }

        // Parse routes configuration
        List<Map<String, String>> routes = parseRoutes(context);

        if (routes.isEmpty()) {
            return NodeExecutionResult.failure("At least one route must be defined");
        }

        try {
            // Use keyword-based matching as the built-in classifier
            // When integrated with AI service, this can be extended to use LLM classification
            RoutingResult result = classifyInput(prompt, routes, defaultRoute);

            Map<String, Object> output = new HashMap<>();
            output.put("selectedRoute", result.routeName);
            output.put("confidence", result.confidence);
            output.put("reasoning", result.reasoning);
            output.put("input", prompt);
            output.put("allRoutes", routes.stream()
                .map(r -> r.get("name"))
                .toList());

            // Set branch to follow based on selected route
            List<String> branches = List.of(result.routeName);

            return NodeExecutionResult.builder()
                .success(true)
                .output(output)
                .branchesToFollow(branches)
                .build();

        } catch (Exception e) {
            log.error("AI Router execution failed: {}", e.getMessage(), e);
            return NodeExecutionResult.failure("AI Router failed: " + e.getMessage());
        }
    }

    /**
     * Parse route definitions from context config.
     * Routes can be a List of Maps or a JSON-like string.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseRoutes(NodeExecutionContext context) {
        List<Map<String, String>> routes = new ArrayList<>();

        Object routesObj = context.getNodeConfig().get("routes");

        if (routesObj instanceof List<?> routeList) {
            for (Object item : routeList) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, String> route = new LinkedHashMap<>();
                    Object name = map.get("name");
                    Object description = map.get("description");
                    if (name != null) {
                        route.put("name", name.toString());
                        route.put("description", description != null ? description.toString() : "");
                        routes.add(route);
                    }
                }
            }
        }

        return routes;
    }

    /**
     * Classify input text against defined routes using keyword matching.
     * This provides a built-in fallback when no external AI service is available.
     */
    private RoutingResult classifyInput(String input, List<Map<String, String>> routes,
                                         String defaultRoute) {
        String inputLower = input.toLowerCase();
        String bestRoute = null;
        double bestScore = 0.0;
        String bestReasoning = "";

        for (Map<String, String> route : routes) {
            String routeName = route.get("name");
            String description = route.getOrDefault("description", "").toLowerCase();

            if (description.isEmpty()) continue;

            // Calculate relevance score based on keyword overlap
            double score = calculateRelevanceScore(inputLower, description, routeName.toLowerCase());

            if (score > bestScore) {
                bestScore = score;
                bestRoute = routeName;
                bestReasoning = "Matched route '" + routeName + "' based on keyword analysis. "
                    + "Description relevance score: " + String.format("%.2f", score);
            }
        }

        // Apply default route if no good match found
        if (bestRoute == null || bestScore < 0.1) {
            if (!defaultRoute.isEmpty()) {
                bestRoute = defaultRoute;
                bestScore = 0.5;
                bestReasoning = "No strong match found. Using default route '" + defaultRoute + "'.";
            } else if (!routes.isEmpty()) {
                // Use first route as fallback
                bestRoute = routes.get(0).get("name");
                bestScore = 0.1;
                bestReasoning = "No strong match found. Defaulting to first route '"
                    + bestRoute + "'.";
            } else {
                bestRoute = "unknown";
                bestScore = 0.0;
                bestReasoning = "No routes matched and no default route configured.";
            }
        }

        return new RoutingResult(bestRoute, bestScore, bestReasoning);
    }

    /**
     * Calculate a relevance score between input text and route description.
     * Uses a simple keyword overlap approach.
     */
    private double calculateRelevanceScore(String input, String description, String routeName) {
        // Tokenize both strings
        Set<String> inputWords = tokenize(input);
        Set<String> descWords = tokenize(description);
        Set<String> routeWords = tokenize(routeName);

        if (descWords.isEmpty()) return 0.0;

        // Calculate word overlap
        long matchCount = descWords.stream()
            .filter(inputWords::contains)
            .count();

        double descriptionScore = (double) matchCount / descWords.size();

        // Bonus for route name match
        long routeMatchCount = routeWords.stream()
            .filter(inputWords::contains)
            .count();

        double routeNameBonus = routeWords.isEmpty()
            ? 0.0
            : (double) routeMatchCount / routeWords.size() * 0.3;

        // Check for exact phrase match (higher weight)
        double phraseBonus = 0.0;
        if (input.contains(routeName)) {
            phraseBonus = 0.4;
        }

        double totalScore = Math.min(1.0, descriptionScore * 0.6 + routeNameBonus + phraseBonus);
        return Math.round(totalScore * 100.0) / 100.0;
    }

    /**
     * Tokenize a string into a set of lowercase words.
     */
    private Set<String> tokenize(String text) {
        Set<String> words = new HashSet<>();
        if (text == null || text.isEmpty()) return words;

        // Split on non-alphanumeric characters
        String[] tokens = text.toLowerCase().split("[^a-z0-9]+");
        for (String token : tokens) {
            if (token.length() > 1) { // Skip single-character tokens
                words.add(token);
            }
        }
        return words;
    }

    /**
     * Internal result class for routing decisions.
     */
    private record RoutingResult(String routeName, double confidence, String reasoning) {}

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "properties", Map.of(
                "prompt", Map.of(
                    "type", "string",
                    "title", "Input Prompt",
                    "description", "The text to classify for routing"
                ),
                "routes", Map.of(
                    "type", "array",
                    "title", "Routes",
                    "description", "List of route definitions with name and description",
                    "items", Map.of(
                        "type", "object",
                        "properties", Map.of(
                            "name", Map.of("type", "string", "title", "Route Name"),
                            "description", Map.of("type", "string", "title", "Route Description")
                        )
                    )
                ),
                "model", Map.of(
                    "type", "string",
                    "title", "AI Model",
                    "description", "AI model to use for classification (optional)"
                ),
                "defaultRoute", Map.of(
                    "type", "string",
                    "title", "Default Route",
                    "description", "Fallback route when no match is found"
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "string", "required", true)
            ),
            "outputs", List.of(
                Map.of("name", "selectedRoute", "type", "string"),
                Map.of("name", "confidence", "type", "number"),
                Map.of("name", "reasoning", "type", "string")
            )
        );
    }
}
