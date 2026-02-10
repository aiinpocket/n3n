package com.aiinpocket.n3n.backup.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RemoteBackupInfo {
    private String filename;
    private Long size;
    private String lastModified;
}
