package com.aiinpocket.n3n.ai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO for AI-recommended node.
 */
@Data
@Builder
public class NodeRecommendation {
    private String nodeType;
    private String displayName;
    private String description;
    private String category;
    private String matchReason;     // AI-generated reason
    private List<String> pros;      // Advantages
    private List<String> cons;      // Considerations
    private String source;          // marketplace, dockerhub, builtin
    private Double rating;
    private Long downloads;
    private boolean needsInstall;
}
