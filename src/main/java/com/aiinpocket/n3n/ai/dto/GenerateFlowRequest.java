package com.aiinpocket.n3n.ai.dto;

import lombok.Data;

/**
 * Request DTO for natural language flow generation.
 */
@Data
public class GenerateFlowRequest {
    private String userInput;       // Natural language description
    private String language;        // Optional: zh-TW, en (defaults to zh-TW)
}
