package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Random number/string generation tool
 */
@Component
@Slf4j
public class RandomTool implements AgentNodeTool {

    private static final SecureRandom random = new SecureRandom();

    @Override
    public String getId() {
        return "random";
    }

    @Override
    public String getName() {
        return "Random Generator";
    }

    @Override
    public String getDescription() {
        return """
                Generate random numbers or strings.

                Operation types:
                - number: Generate random integer
                - float: Generate random floating-point number
                - string: Generate random string
                - password: Generate secure password
                - pick: Randomly pick from a list

                Parameters:
                - type: Operation type
                - min: Minimum value (for number/float)
                - max: Maximum value (for number/float)
                - length: String length (for string/password)
                - count: Number to generate (default 1)
                - items: Option list (for pick, comma-separated)
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "type", Map.of(
                                "type", "string",
                                "enum", List.of("number", "float", "string", "password", "pick"),
                                "description", "Random type",
                                "default", "number"
                        ),
                        "min", Map.of(
                                "type", "number",
                                "description", "Minimum value",
                                "default", 0
                        ),
                        "max", Map.of(
                                "type", "number",
                                "description", "Maximum value",
                                "default", 100
                        ),
                        "length", Map.of(
                                "type", "integer",
                                "description", "String length",
                                "default", 16
                        ),
                        "count", Map.of(
                                "type", "integer",
                                "description", "Number to generate",
                                "default", 1
                        ),
                        "items", Map.of(
                                "type", "string",
                                "description", "Option list (comma-separated)"
                        )
                ),
                "required", List.of()
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String type = (String) parameters.getOrDefault("type", "number");
                int count = Math.min(100, Math.max(1,
                        parameters.containsKey("count") ? ((Number) parameters.get("count")).intValue() : 1));

                List<Object> results = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    results.add(generateRandom(type, parameters));
                }

                StringBuilder sb = new StringBuilder();
                if (count == 1) {
                    sb.append("Generated random value: ").append(results.get(0));
                } else {
                    sb.append(String.format("Generated %d random values:\n", count));
                    for (int i = 0; i < results.size(); i++) {
                        sb.append(String.format("%d. %s\n", i + 1, results.get(i)));
                    }
                }

                return ToolResult.success(sb.toString(), Map.of(
                        "results", results,
                        "type", type,
                        "count", count
                ));

            } catch (Exception e) {
                log.error("Random generation failed", e);
                return ToolResult.failure("Random generation failed");
            }
        });
    }

    private Object generateRandom(String type, Map<String, Object> parameters) {
        return switch (type) {
            case "number" -> {
                int min = parameters.containsKey("min") ? ((Number) parameters.get("min")).intValue() : 0;
                int max = parameters.containsKey("max") ? ((Number) parameters.get("max")).intValue() : 100;
                yield min + random.nextInt(max - min + 1);
            }
            case "float" -> {
                double min = parameters.containsKey("min") ? ((Number) parameters.get("min")).doubleValue() : 0.0;
                double max = parameters.containsKey("max") ? ((Number) parameters.get("max")).doubleValue() : 1.0;
                yield min + random.nextDouble() * (max - min);
            }
            case "string" -> {
                int length = Math.min(256, Math.max(1,
                        parameters.containsKey("length") ? ((Number) parameters.get("length")).intValue() : 16));
                String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < length; i++) {
                    sb.append(chars.charAt(random.nextInt(chars.length())));
                }
                yield sb.toString();
            }
            case "password" -> {
                int length = Math.min(128, Math.max(8,
                        parameters.containsKey("length") ? ((Number) parameters.get("length")).intValue() : 16));
                String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789!@#$%^&*()_+-=[]{}|;:,.<>?";
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < length; i++) {
                    sb.append(chars.charAt(random.nextInt(chars.length())));
                }
                yield sb.toString();
            }
            case "pick" -> {
                String itemsStr = (String) parameters.get("items");
                if (itemsStr == null || itemsStr.isBlank()) {
                    yield "Error: items parameter is required";
                }
                String[] items = itemsStr.split(",");
                yield items[random.nextInt(items.length)].trim();
            }
            default -> throw new IllegalArgumentException("Unsupported type: " + type);
        };
    }

    @Override
    public String getCategory() {
        return "utility";
    }
}
