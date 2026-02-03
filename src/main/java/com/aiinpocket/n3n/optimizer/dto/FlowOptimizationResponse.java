package com.aiinpocket.n3n.optimizer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowOptimizationResponse {

    private boolean success;
    private List<OptimizationSuggestion> suggestions;
    private String error;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizationSuggestion {
        private String type;        // "parallel", "merge", "remove", "reorder"
        private String title;
        private String description;
        private List<String> affectedNodes;
        private int priority;       // 1 = high, 2 = medium, 3 = low
    }

    public static FlowOptimizationResponse error(String message) {
        return FlowOptimizationResponse.builder()
            .success(false)
            .error(message)
            .build();
    }

    public static FlowOptimizationResponse disabled() {
        return FlowOptimizationResponse.builder()
            .success(true)
            .suggestions(List.of())
            .build();
    }
}
