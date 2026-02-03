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
public class ApplySuggestionsResponse {
    private boolean success;
    private int appliedCount;
    private List<String> appliedSuggestions;
    private Map<String, Object> updatedDefinition;
    private String error;

    public static ApplySuggestionsResponse success(int count, List<String> applied, Map<String, Object> definition) {
        return ApplySuggestionsResponse.builder()
            .success(true)
            .appliedCount(count)
            .appliedSuggestions(applied)
            .updatedDefinition(definition)
            .build();
    }

    public static ApplySuggestionsResponse error(String message) {
        return ApplySuggestionsResponse.builder()
            .success(false)
            .error(message)
            .build();
    }
}
