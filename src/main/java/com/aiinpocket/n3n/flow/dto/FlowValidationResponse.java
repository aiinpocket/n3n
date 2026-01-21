package com.aiinpocket.n3n.flow.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
public class FlowValidationResponse {
    private boolean valid;
    private List<String> errors;
    private List<String> warnings;
    private List<String> entryPoints;
    private List<String> exitPoints;
    private List<String> executionOrder;
    private Map<String, Set<String>> dependencies;
}
