package com.aiinpocket.n3n.backup.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ImportSyncRequest {
    @NotBlank
    private String recoveryKeyPhrase;
}
