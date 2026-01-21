package com.aiinpocket.n3n.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class CreateWebhookRequest {

    @NotNull(message = "Flow ID is required")
    private UUID flowId;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Path is required")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Path can only contain alphanumeric characters, hyphens, and underscores")
    private String path;

    @Pattern(regexp = "^(GET|POST|PUT|PATCH|DELETE)$", message = "Method must be GET, POST, PUT, PATCH or DELETE")
    private String method = "POST";

    private String authType;

    private Map<String, Object> authConfig;
}
