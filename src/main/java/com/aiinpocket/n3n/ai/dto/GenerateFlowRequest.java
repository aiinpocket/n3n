package com.aiinpocket.n3n.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * Request DTO for natural language flow generation.
 */
@Data
public class GenerateFlowRequest {
    @NotBlank
    @Size(max = 10000)
    private String userInput;       // Natural language description
    private String language;        // Optional: zh-TW, en (defaults to zh-TW)

    /**
     * Structured requirements from the clarification conversation.
     * When present, provides richer context for more accurate flow generation.
     */
    private RequirementContext requirementContext;

    @Data
    public static class RequirementContext {
        private String triggerType;         // e.g. "schedule", "webhook", "manual"
        private String triggerDescription;  // e.g. "Every 5 minutes"
        private String dataSource;          // e.g. "REST API at https://example.com/api"
        private List<String> processSteps;  // e.g. ["Filter by status", "Transform to CSV"]
        private String outputTarget;        // e.g. "Send email notification"
        private String errorHandling;       // e.g. "Retry 3 times, then notify admin"
        private String fullDescription;     // Complete requirement description from clarification
    }
}
