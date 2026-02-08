package com.aiinpocket.n3n.execution.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class CreateExecutionRequest {
    @NotNull(message = "Flow ID is required")
    private UUID flowId;

    @Size(max = 50, message = "Version must be at most 50 characters")
    private String version; // If null, uses published version

    @Size(max = 100, message = "Input must have at most 100 fields")
    private Map<String, Object> input;

    @Size(max = 50, message = "Context must have at most 50 fields")
    private Map<String, Object> context;
}
