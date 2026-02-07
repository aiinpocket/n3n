package com.aiinpocket.n3n.credential.dto;

import com.aiinpocket.n3n.common.constant.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;
import java.util.UUID;

@Data
public class CreateCredentialRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Type is required")
    private String type;

    private String description;

    private UUID workspaceId;

    private String visibility = Status.Visibility.PRIVATE;

    @NotNull(message = "Credential data is required")
    private Map<String, Object> data;
}
