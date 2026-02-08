package com.aiinpocket.n3n.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class CreateWebhookRequest {

    @NotNull(message = "Flow ID is required")
    private UUID flowId;

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @NotBlank(message = "Path is required")
    @Size(max = 500, message = "Path must be at most 500 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Path can only contain alphanumeric characters, hyphens, and underscores")
    private String path;

    @Pattern(regexp = "^(GET|POST|PUT|PATCH|DELETE)$", message = "Method must be GET, POST, PUT, PATCH or DELETE")
    private String method = "POST";

    @Size(max = 20, message = "Auth type must be at most 20 characters")
    private String authType;

    @Size(max = 20, message = "Auth config must have at most 20 fields")
    private Map<String, Object> authConfig;
}
