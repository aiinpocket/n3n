package com.aiinpocket.n3n.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplySuggestionsRequest {
    private String flowId;
    private String version;
    private List<String> suggestionIds;
    private Map<String, Object> definition;  // Current flow definition
    private List<SuggestionInfo> suggestions;  // Suggestions to apply

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuggestionInfo {
        private String id;
        private String type;
        private String title;
        private String description;
        private List<String> affectedNodes;
    }
}
