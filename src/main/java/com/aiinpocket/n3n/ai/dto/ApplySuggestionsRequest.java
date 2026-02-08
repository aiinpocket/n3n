package com.aiinpocket.n3n.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
    @NotBlank
    private String flowId;

    private String version;

    @NotEmpty(message = "Suggestion IDs required")
    @Size(max = 100, message = "Too many suggestions")
    private List<String> suggestionIds;

    private Map<String, Object> definition;

    @Valid
    @Size(max = 100)
    private List<SuggestionInfo> suggestions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SuggestionInfo {
        @NotBlank
        private String id;

        @NotBlank
        private String type;

        @Size(max = 200)
        private String title;

        @Size(max = 2000)
        private String description;

        @Size(max = 100)
        private List<String> affectedNodes;
    }
}
