package com.aiinpocket.n3n.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request DTO for natural language flow generation.
 */
@Data
public class GenerateFlowRequest {
    @NotBlank
    @Size(max = 10000)
    private String userInput;       // Natural language description
    private String language;        // Optional: zh-TW, en (defaults to zh-TW)
}
