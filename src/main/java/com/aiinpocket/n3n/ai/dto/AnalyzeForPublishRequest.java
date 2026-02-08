package com.aiinpocket.n3n.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyzeForPublishRequest {
    @NotNull(message = "Flow definition is required")
    @Size(max = 500, message = "Flow definition must have at most 500 fields")
    private Map<String, Object> definition;  // Flow definition (nodes, edges)
    @Size(max = 36, message = "Flow ID must be at most 36 characters")
    private String flowId;
    @Size(max = 50, message = "Version must be at most 50 characters")
    private String version;
}
