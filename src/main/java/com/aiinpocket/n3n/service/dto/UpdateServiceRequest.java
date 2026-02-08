package com.aiinpocket.n3n.service.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateServiceRequest {

    @Size(max = 255, message = "Display name must be at most 255 characters")
    private String displayName;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    @Size(max = 2000, message = "Base URL must be at most 2000 characters")
    private String baseUrl;

    @Size(max = 2000, message = "Schema URL must be at most 2000 characters")
    private String schemaUrl;

    @Pattern(regexp = "^(none|api_key|bearer|basic|oauth2)$", message = "Invalid auth type")
    private String authType;

    @Size(max = 20, message = "Auth config must have at most 20 fields")
    private Map<String, Object> authConfig;

    @Size(max = 10, message = "Health check must have at most 10 fields")
    private Map<String, Object> healthCheck;

    @Pattern(regexp = "^(active|inactive)$", message = "Status must be active or inactive")
    private String status;
}
