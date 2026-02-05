package com.aiinpocket.n3n.ai.codex;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Node Codex - Extended node information for AI understanding.
 * Similar to n8n's Codex system, provides rich metadata for AI to better understand
 * and recommend nodes.
 */
@Data
@Builder
public class NodeCodex {

    // Basic info (from NodeHandlerInfo)
    private String type;
    private String displayName;
    private String description;
    private String category;
    private String icon;
    private boolean isTrigger;
    private boolean supportsAsync;

    // Extended AI-friendly info
    private List<String> keywords;           // Search keywords (zh-TW and en)
    private List<String> useCases;           // Common use cases (zh-TW)
    private List<NodeExample> examples;      // Configuration examples
    private List<String> relatedNodes;       // Related/complementary nodes
    private List<String> inputFormats;       // Expected input data formats
    private List<String> outputFormats;      // Output data formats
    private String bestPractices;            // Best practices and tips (zh-TW)
    private List<String> commonPatterns;     // Common workflow patterns using this node

    // Configuration schema
    private Map<String, Object> configSchema;
    private Map<String, Object> interfaceDefinition;

    /**
     * Node configuration example
     */
    @Data
    @Builder
    public static class NodeExample {
        private String scenario;                 // Use case scenario (zh-TW)
        private String description;              // Detailed description
        private Map<String, Object> config;      // Example configuration
        private String expectedInput;            // Expected input format
        private String expectedOutput;           // Expected output format
    }

    /**
     * Create a basic codex from NodeHandlerInfo
     */
    public static NodeCodex fromBasicInfo(
            String type,
            String displayName,
            String description,
            String category,
            String icon,
            boolean isTrigger,
            boolean supportsAsync,
            Map<String, Object> configSchema) {
        return NodeCodex.builder()
                .type(type)
                .displayName(displayName)
                .description(description)
                .category(category)
                .icon(icon)
                .isTrigger(isTrigger)
                .supportsAsync(supportsAsync)
                .configSchema(configSchema)
                .keywords(List.of())
                .useCases(List.of())
                .examples(List.of())
                .relatedNodes(List.of())
                .inputFormats(List.of())
                .outputFormats(List.of())
                .commonPatterns(List.of())
                .build();
    }

    /**
     * Check if this node matches a search query
     */
    public boolean matchesQuery(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }

        String lowerQuery = query.toLowerCase();

        // Check type
        if (type != null && type.toLowerCase().contains(lowerQuery)) {
            return true;
        }

        // Check display name
        if (displayName != null && displayName.toLowerCase().contains(lowerQuery)) {
            return true;
        }

        // Check description
        if (description != null && description.toLowerCase().contains(lowerQuery)) {
            return true;
        }

        // Check keywords
        if (keywords != null) {
            for (String keyword : keywords) {
                if (keyword.toLowerCase().contains(lowerQuery)) {
                    return true;
                }
            }
        }

        // Check use cases
        if (useCases != null) {
            for (String useCase : useCases) {
                if (useCase.toLowerCase().contains(lowerQuery)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Calculate relevance score for a query (0.0 - 1.0)
     */
    public double calculateRelevance(String query) {
        if (query == null || query.isBlank()) {
            return 0.5;
        }

        String lowerQuery = query.toLowerCase();
        double score = 0.0;

        // Type exact match (highest weight)
        if (type != null && type.equalsIgnoreCase(query)) {
            score += 1.0;
        } else if (type != null && type.toLowerCase().contains(lowerQuery)) {
            score += 0.5;
        }

        // Display name match
        if (displayName != null && displayName.toLowerCase().contains(lowerQuery)) {
            score += 0.3;
        }

        // Keyword match
        if (keywords != null) {
            for (String keyword : keywords) {
                if (keyword.equalsIgnoreCase(query)) {
                    score += 0.4;
                    break;
                } else if (keyword.toLowerCase().contains(lowerQuery)) {
                    score += 0.2;
                    break;
                }
            }
        }

        // Use case match
        if (useCases != null) {
            for (String useCase : useCases) {
                if (useCase.toLowerCase().contains(lowerQuery)) {
                    score += 0.2;
                    break;
                }
            }
        }

        // Category match
        if (category != null && category.toLowerCase().contains(lowerQuery)) {
            score += 0.1;
        }

        return Math.min(1.0, score);
    }

    /**
     * Generate a prompt-friendly description
     */
    public String toPromptDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(displayName).append(" (").append(type).append(")\n");
        sb.append("- 分類: ").append(category).append("\n");
        sb.append("- 說明: ").append(description).append("\n");

        if (keywords != null && !keywords.isEmpty()) {
            sb.append("- 關鍵字: ").append(String.join(", ", keywords)).append("\n");
        }

        if (useCases != null && !useCases.isEmpty()) {
            sb.append("- 用途:\n");
            for (String useCase : useCases) {
                sb.append("  * ").append(useCase).append("\n");
            }
        }

        if (relatedNodes != null && !relatedNodes.isEmpty()) {
            sb.append("- 相關節點: ").append(String.join(", ", relatedNodes)).append("\n");
        }

        if (bestPractices != null && !bestPractices.isBlank()) {
            sb.append("- 最佳實踐: ").append(bestPractices).append("\n");
        }

        return sb.toString();
    }
}
