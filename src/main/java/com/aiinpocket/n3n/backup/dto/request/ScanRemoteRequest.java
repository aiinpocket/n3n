package com.aiinpocket.n3n.backup.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ScanRemoteRequest {
    @NotBlank
    private String recoveryKeyPhrase;
}
