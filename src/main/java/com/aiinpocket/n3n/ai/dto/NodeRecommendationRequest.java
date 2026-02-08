package com.aiinpocket.n3n.ai.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * Request DTO for node recommendation.
 */
@Data
public class NodeRecommendationRequest {
    private Map<String, Object> currentFlow;
    @Size(max = 500)
    private String searchQuery;
    @Size(max = 100)
    private String category;   // Optional: filter by category
}
