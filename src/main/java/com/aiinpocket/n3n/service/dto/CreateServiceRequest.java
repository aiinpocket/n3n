package com.aiinpocket.n3n.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class CreateServiceRequest {

    @NotBlank(message = "Service name is required")
    @Pattern(regexp = "^[a-z][a-z0-9-]*$", message = "Name must start with a letter and contain only lowercase letters, numbers, and hyphens")
    private String name;

    @NotBlank(message = "Display name is required")
    private String displayName;

    private String description;

    @NotBlank(message = "Base URL is required")
    private String baseUrl;

    @Pattern(regexp = "^(REST|GraphQL|gRPC)$", message = "Protocol must be REST, GraphQL, or gRPC")
    private String protocol = "REST";

    private String schemaUrl;

    @Pattern(regexp = "^(none|api_key|bearer|basic|oauth2)$", message = "Invalid auth type")
    private String authType;

    private Map<String, Object> authConfig;

    private Map<String, Object> healthCheck;

    private List<CreateEndpointRequest> endpoints;
}
