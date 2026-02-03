package com.aiinpocket.n3n.ai.dto;

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
    private Map<String, Object> definition;  // Flow definition (nodes, edges)
    private String flowId;
    private String version;
}
