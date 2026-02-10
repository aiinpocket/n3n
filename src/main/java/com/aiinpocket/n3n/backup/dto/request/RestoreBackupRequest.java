package com.aiinpocket.n3n.backup.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class RestoreBackupRequest {

    @NotBlank(message = "Recovery key phrase is required")
    private String recoveryKeyPhrase;

    @NotBlank(message = "Filename is required")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Filename contains invalid characters")
    private String filename;
}
