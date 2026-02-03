package com.aiinpocket.n3n.credential.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * Request DTO for testing unsaved credential connection
 */
@Data
public class TestCredentialRequest {

    @NotBlank(message = "Credential type is required")
    private String type;  // mongodb, postgres, mysql, redis, database

    @NotNull(message = "Credential data is required")
    private Map<String, Object> data;
}
