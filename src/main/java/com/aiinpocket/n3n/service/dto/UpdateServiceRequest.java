package com.aiinpocket.n3n.service.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateServiceRequest {

    private String displayName;

    private String description;

    private String baseUrl;

    private String schemaUrl;

    @Pattern(regexp = "^(none|api_key|bearer|basic|oauth2)$", message = "Invalid auth type")
    private String authType;

    private Map<String, Object> authConfig;

    private Map<String, Object> healthCheck;

    @Pattern(regexp = "^(active|inactive)$", message = "Status must be active or inactive")
    private String status;
}
