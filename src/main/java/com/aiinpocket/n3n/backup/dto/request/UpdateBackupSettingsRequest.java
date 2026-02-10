package com.aiinpocket.n3n.backup.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateBackupSettingsRequest {

    private Boolean enabled;

    @Pattern(regexp = "sftp|s3|r2|gcs", message = "Provider must be one of: sftp, s3, r2, gcs")
    private String provider;

    @Size(max = 500)
    private String endpoint;

    @Size(max = 200)
    private String bucket;

    @Size(max = 500)
    private String basePath;

    // S3 / R2
    @Size(max = 500)
    private String accessKey;

    @Size(max = 500)
    private String secretKey;

    @Size(max = 50)
    private String region;

    // GCS
    @Size(max = 10000, message = "Service account JSON too large")
    private String serviceAccountJson;

    // SFTP
    @Size(max = 200)
    private String sftpHost;

    private Integer sftpPort;

    @Size(max = 200)
    private String sftpUsername;

    @Size(max = 500)
    private String sftpPassword;

    @Size(max = 10000, message = "Private key too large")
    private String sftpPrivateKey;

    @Size(max = 500)
    private String sftpPath;

    @Size(max = 50)
    private String schedule;
}
