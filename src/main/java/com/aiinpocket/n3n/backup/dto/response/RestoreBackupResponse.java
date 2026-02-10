package com.aiinpocket.n3n.backup.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RestoreBackupResponse {
    private String message;
}
