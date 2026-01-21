package com.aiinpocket.n3n.component.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class CreateVersionRequest {
    @NotBlank(message = "Version is required")
    private String version;

    @NotBlank(message = "Image is required")
    private String image;

    @NotNull(message = "Interface definition is required")
    private Map<String, Object> interfaceDef;

    private Map<String, Object> configSchema;

    private Map<String, Object> resources;

    private Map<String, Object> healthCheck;
}
