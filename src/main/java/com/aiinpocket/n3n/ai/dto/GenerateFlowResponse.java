package com.aiinpocket.n3n.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Response DTO for natural language flow generation.
 */
@Data
@Builder
public class GenerateFlowResponse {
    private boolean success;
    private boolean aiAvailable;
    private String understanding;               // AI's understanding of the request
    private Map<String, Object> flowDefinition; // Generated flow { nodes, edges }
    private List<String> requiredNodes;         // Node types required for this flow
    private List<String> missingNodes;          // Node types not available/installed
    private String error;

    public static GenerateFlowResponse success(
            String understanding,
            Map<String, Object> definition,
            List<String> required,
            List<String> missing) {
        return GenerateFlowResponse.builder()
                .success(true)
                .aiAvailable(true)
                .understanding(understanding)
                .flowDefinition(definition)
                .requiredNodes(required)
                .missingNodes(missing)
                .build();
    }

    public static GenerateFlowResponse aiUnavailable() {
        return GenerateFlowResponse.builder()
                .success(true)
                .aiAvailable(false)
                .error("AI service unavailable")
                .build();
    }

    public static GenerateFlowResponse error(String message) {
        return GenerateFlowResponse.builder()
                .success(false)
                .aiAvailable(false)
                .error(message)
                .build();
    }
}
