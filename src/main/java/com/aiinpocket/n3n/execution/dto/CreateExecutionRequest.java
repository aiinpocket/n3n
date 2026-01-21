package com.aiinpocket.n3n.execution.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class CreateExecutionRequest {
    @NotNull(message = "Flow ID is required")
    private UUID flowId;

    private String version; // If null, uses published version

    private Map<String, Object> input;

    private Map<String, Object> context;
}
