package com.aiinpocket.n3n.backup.dto.response;

import com.aiinpocket.n3n.backup.entity.BackupSettings;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class BackupSettingsResponse {
    private Boolean enabled;
    private String provider;
    private String endpoint;
    private String bucket;
    private String basePath;
    private String region;
    // 敏感欄位只回傳是否已設定
    private Boolean hasAccessKey;
    private Boolean hasSecretKey;
    private Boolean hasServiceAccountJson;
    // SFTP
    private String sftpHost;
    private Integer sftpPort;
    private String sftpUsername;
    private Boolean hasSftpPassword;
    private Boolean hasSftpPrivateKey;
    private String sftpPath;
    // 排程
    private String schedule;
    private Instant lastBackupAt;
    private Instant updatedAt;

    public static BackupSettingsResponse from(BackupSettings entity) {
        return BackupSettingsResponse.builder()
                .enabled(entity.getEnabled())
                .provider(entity.getProvider())
                .endpoint(entity.getEndpoint())
                .bucket(entity.getBucket())
                .basePath(entity.getBasePath())
                .region(entity.getRegion())
                .hasAccessKey(entity.getAccessKey() != null && !entity.getAccessKey().isBlank())
                .hasSecretKey(entity.getSecretKey() != null && !entity.getSecretKey().isBlank())
                .hasServiceAccountJson(entity.getServiceAccountJson() != null && !entity.getServiceAccountJson().isBlank())
                .sftpHost(entity.getSftpHost())
                .sftpPort(entity.getSftpPort())
                .sftpUsername(entity.getSftpUsername())
                .hasSftpPassword(entity.getSftpPassword() != null && !entity.getSftpPassword().isBlank())
                .hasSftpPrivateKey(entity.getSftpPrivateKey() != null && !entity.getSftpPrivateKey().isBlank())
                .sftpPath(entity.getSftpPath())
                .schedule(entity.getSchedule())
                .lastBackupAt(entity.getLastBackupAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
