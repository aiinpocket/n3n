package com.aiinpocket.n3n.flow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * Request to pin data to a specific node in a flow version.
 * Pinned data can be used as test data during development.
 */
@Data
public class PinDataRequest {
    @NotBlank(message = "Node ID is required")
    private String nodeId;

    @NotNull(message = "Data is required")
    private Map<String, Object> data;
}
