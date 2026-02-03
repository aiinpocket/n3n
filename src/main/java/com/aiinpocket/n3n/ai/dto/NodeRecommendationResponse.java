package com.aiinpocket.n3n.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response DTO for node recommendation.
 */
@Data
@Builder
public class NodeRecommendationResponse {
    private boolean success;
    private boolean aiAvailable;
    private List<NodeCategoryInfo> categories;
    private List<InstalledNodeInfo> installedNodes;
    private List<NodeRecommendation> aiRecommendations;
    private List<NodeRecommendation> marketplaceResults;
    private String error;

    public static NodeRecommendationResponse success(
            List<NodeCategoryInfo> categories,
            List<InstalledNodeInfo> installed,
            List<NodeRecommendation> aiRecs,
            List<NodeRecommendation> marketplace) {
        return NodeRecommendationResponse.builder()
                .success(true)
                .aiAvailable(true)
                .categories(categories)
                .installedNodes(installed)
                .aiRecommendations(aiRecs)
                .marketplaceResults(marketplace)
                .build();
    }

    public static NodeRecommendationResponse aiUnavailable(
            List<NodeCategoryInfo> categories,
            List<InstalledNodeInfo> installed) {
        return NodeRecommendationResponse.builder()
                .success(true)
                .aiAvailable(false)
                .categories(categories)
                .installedNodes(installed)
                .aiRecommendations(List.of())
                .marketplaceResults(List.of())
                .build();
    }

    public static NodeRecommendationResponse error(String message) {
        return NodeRecommendationResponse.builder()
                .success(false)
                .aiAvailable(false)
                .error(message)
                .build();
    }
}
