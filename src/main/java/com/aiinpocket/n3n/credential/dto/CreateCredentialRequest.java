package com.aiinpocket.n3n.credential.dto;

import com.aiinpocket.n3n.common.constant.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class CreateCredentialRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    @NotBlank(message = "Type is required")
    @Size(max = 100, message = "Type must be at most 100 characters")
    private String type;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    private UUID workspaceId;

    private String visibility = Status.Visibility.PRIVATE;

    @NotNull(message = "Credential data is required")
    @Size(max = 50, message = "Credential data must have at most 50 fields")
    private Map<String, Object> data;
}
