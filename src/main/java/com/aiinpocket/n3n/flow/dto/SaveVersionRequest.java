package com.aiinpocket.n3n.flow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class SaveVersionRequest {
    @NotBlank(message = "Version is required")
    private String version;

    @NotNull(message = "Definition is required")
    private Map<String, Object> definition;

    private Map<String, Object> settings;
}
