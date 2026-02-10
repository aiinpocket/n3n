package com.aiinpocket.n3n.backup.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ListRemoteRequest {

    @NotBlank(message = "Recovery key phrase is required")
    private String recoveryKeyPhrase;
}
