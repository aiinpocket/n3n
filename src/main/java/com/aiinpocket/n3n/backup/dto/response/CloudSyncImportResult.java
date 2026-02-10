package com.aiinpocket.n3n.backup.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CloudSyncImportResult {
    private int flowsImported;
    private int credentialsImported;
    private int aiProvidersImported;
    private int skipped;
    private int failed;
    private List<String> errors;
}
