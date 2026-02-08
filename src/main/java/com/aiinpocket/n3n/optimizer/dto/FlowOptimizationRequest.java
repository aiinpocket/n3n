package com.aiinpocket.n3n.optimizer.dto;

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
public class FlowOptimizationRequest {

    @NotNull(message = "Flow definition is required")
    @Size(max = 500, message = "Flow definition must have at most 500 fields")
    private Map<String, Object> definition;
}
