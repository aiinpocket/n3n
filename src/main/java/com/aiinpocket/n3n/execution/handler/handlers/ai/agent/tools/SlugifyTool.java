package com.aiinpocket.n3n.execution.handler.handlers.ai.agent.tools;

import com.aiinpocket.n3n.execution.handler.handlers.ai.agent.AgentNodeTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * URL Slug generation tool
 * Converts text into a URL-friendly slug
 */
@Component
@Slf4j
public class SlugifyTool implements AgentNodeTool {

    @Override
    public String getId() {
        return "slugify";
    }

    @Override
    public String getName() {
        return "Slugify";
    }

    @Override
    public String getDescription() {
        return """
                Converts text into a URL-friendly slug.

                Features:
                - Removes diacritical marks (accents)
                - Converts to lowercase
                - Replaces spaces and special characters with hyphens
                - Removes consecutive hyphens
                - Removes leading and trailing hyphens

                Parameters:
                - text: Text to convert
                - separator: Separator character (default: -)
                - lowercase: Whether to convert to lowercase (default: true)
                - maxLength: Maximum length (default: 100)

                Examples:
                "Hello World!" → "hello-world"
                "Café Résumé" → "cafe-resume"
                """;
    }

    @Override
    public Map<String, Object> getParametersSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "text", Map.of(
                                "type", "string",
                                "description", "Text to convert"
                        ),
                        "separator", Map.of(
                                "type", "string",
                                "description", "Separator character",
                                "default", "-"
                        ),
                        "lowercase", Map.of(
                                "type", "boolean",
                                "description", "Whether to convert to lowercase",
                                "default", true
                        ),
                        "maxLength", Map.of(
                                "type", "integer",
                                "description", "Maximum length",
                                "default", 100
                        )
                ),
                "required", List.of("text")
        );
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters, ToolExecutionContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String text = (String) parameters.get("text");
                String separator = (String) parameters.getOrDefault("separator", "-");
                boolean lowercase = !Boolean.FALSE.equals(parameters.get("lowercase"));
                int maxLength = parameters.containsKey("maxLength")
                        ? ((Number) parameters.get("maxLength")).intValue()
                        : 100;

                if (text == null || text.isEmpty()) {
                    return ToolResult.failure("Text cannot be empty");
                }

                // Security: limit separator length
                if (separator.length() > 3) {
                    return ToolResult.failure("Separator must be at most 3 characters");
                }

                // Limit max length
                maxLength = Math.min(maxLength, 500);

                String slug = slugify(text, separator, lowercase, maxLength);

                return ToolResult.success(
                        String.format("Slug generated successfully\nOriginal: %s\nSlug: %s", text, slug),
                        Map.of(
                                "slug", slug,
                                "original", text,
                                "separator", separator,
                                "length", slug.length()
                        )
                );

            } catch (Exception e) {
                log.error("Slugify failed", e);
                return ToolResult.failure("Slug generation failed");
            }
        });
    }

    private String slugify(String text, String separator, boolean lowercase, int maxLength) {
        // Normalize unicode (remove accents)
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // Convert to lowercase if needed
        if (lowercase) {
            normalized = normalized.toLowerCase();
        }

        // Replace non-alphanumeric characters with separator
        String slug = normalized.replaceAll("[^a-zA-Z0-9]+", separator);

        // Remove consecutive separators
        String escapedSep = java.util.regex.Pattern.quote(separator);
        slug = slug.replaceAll(escapedSep + "+", separator);

        // Remove leading/trailing separators
        slug = slug.replaceAll("^" + escapedSep + "|" + escapedSep + "$", "");

        // Truncate to max length
        if (slug.length() > maxLength) {
            slug = slug.substring(0, maxLength);
            // Remove trailing separator after truncation
            slug = slug.replaceAll(escapedSep + "$", "");
        }

        return slug;
    }

    @Override
    public String getCategory() {
        return "text";
    }
}
