package com.aiinpocket.n3n.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishAnalysisResponse {

    private boolean success;
    private FlowSummary summary;
    private List<OptimizationSuggestion> suggestions;
    private Long analysisTimeMs;
    private String error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FlowSummary {
        private int nodeCount;
        private int edgeCount;
        private String version;
        private List<String> nodeTypes;
        private boolean hasUnconnectedNodes;
        private boolean hasCycles;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizationSuggestion {
        private String id;
        private String type;          // parallel, merge, remove, reorder
        private String title;
        private String description;
        private String benefit;       // "可減少約 40% 執行時間"
        private int priority;         // 1=高, 2=中, 3=低
        private List<String> affectedNodes;
    }

    public static PublishAnalysisResponse error(String message) {
        return PublishAnalysisResponse.builder()
            .success(false)
            .error(message)
            .build();
    }

    public static PublishAnalysisResponse disabled() {
        return PublishAnalysisResponse.builder()
            .success(true)
            .suggestions(List.of())
            .build();
    }
}
