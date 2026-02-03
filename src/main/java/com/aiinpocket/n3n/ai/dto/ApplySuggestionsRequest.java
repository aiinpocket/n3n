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
public class ApplySuggestionsRequest {
    private String flowId;
    private String version;
    private List<String> suggestionIds;
}
