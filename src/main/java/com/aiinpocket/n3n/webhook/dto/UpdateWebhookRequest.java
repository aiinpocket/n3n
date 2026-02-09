package com.aiinpocket.n3n.webhook.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Map;

@Data
public class UpdateWebhookRequest {
    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @Size(max = 20, message = "Auth type must be at most 20 characters")
    private String authType;

    @Size(max = 20, message = "Auth config must have at most 20 fields")
    private Map<String, Object> authConfig;
}
