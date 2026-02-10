package com.aiinpocket.n3n.backup.dto.response;

import com.aiinpocket.n3n.backup.entity.BackupHistory;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class BackupHistoryResponse {
    private UUID id;
    private String filename;
    private Long fileSize;
    private String provider;
    private String checksum;
    private String status;
    private String errorMessage;
    private Instant createdAt;

    public static BackupHistoryResponse from(BackupHistory entity) {
        return BackupHistoryResponse.builder()
                .id(entity.getId())
                .filename(entity.getFilename())
                .fileSize(entity.getFileSize())
                .provider(entity.getProvider())
                .checksum(entity.getChecksum())
                .status(entity.getStatus())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
