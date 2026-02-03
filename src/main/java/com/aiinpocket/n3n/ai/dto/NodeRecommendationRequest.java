package com.aiinpocket.n3n.ai.dto;

import lombok.Data;

import java.util.Map;

/**
 * Request DTO for node recommendation.
 */
@Data
public class NodeRecommendationRequest {
    private Map<String, Object> currentFlow;
    private String searchQuery;
    private String category;   // Optional: filter by category
}
