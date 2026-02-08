package com.aiinpocket.n3n.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateServiceRequest {

    @NotBlank(message = "Service name is required")
    @Size(max = 100, message = "Name must be at most 100 characters")
    @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "Name must start with a letter and contain only lowercase letters, numbers, and hyphens")
    private String name;

    @NotBlank(message = "Display name is required")
    @Size(max = 255, message = "Display name must be at most 255 characters")
    private String displayName;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    @NotBlank(message = "Base URL is required")
    @Size(max = 2000, message = "Base URL must be at most 2000 characters")
    private String baseUrl;

    @Pattern(regexp = "^(REST|GraphQL|gRPC)$", message = "Protocol must be REST, GraphQL, or gRPC")
    private String protocol = "REST";

    @Size(max = 2000, message = "Schema URL must be at most 2000 characters")
    private String schemaUrl;

    @Pattern(regexp = "^(none|api_key|bearer|basic|oauth2)$", message = "Invalid auth type")
    private String authType;

    @Size(max = 20, message = "Auth config must have at most 20 fields")
    private Map<String, Object> authConfig;

    @Size(max = 10, message = "Health check must have at most 10 fields")
    private Map<String, Object> healthCheck;

    @Size(max = 100, message = "Endpoints list must have at most 100 items")
    private List<CreateEndpointRequest> endpoints;
}
