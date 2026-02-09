package com.aiinpocket.n3n.credential.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Map;

@Data
public class UpdateCredentialRequest {
    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    private String visibility;

    @Size(max = 50, message = "Credential data must have at most 50 fields")
    private Map<String, Object> data;
}
