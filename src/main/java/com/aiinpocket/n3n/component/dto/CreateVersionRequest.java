package com.aiinpocket.n3n.component.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class CreateVersionRequest {
    @NotBlank(message = "Version is required")
    @Size(max = 50, message = "Version must be at most 50 characters")
    private String version;

    @NotBlank(message = "Image is required")
    @Size(max = 500, message = "Image must be at most 500 characters")
    private String image;

    @NotNull(message = "Interface definition is required")
    private Map<String, Object> interfaceDef;

    private Map<String, Object> configSchema;

    private Map<String, Object> resources;

    private Map<String, Object> healthCheck;
}
