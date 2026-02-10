package com.aiinpocket.n3n.backup.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * 雲端備份設定（單例，只有一筆記錄）
 */
@Entity
@Table(name = "backup_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupSettings {

    @Id
    @Builder.Default
    private Long id = 1L;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = false;

    /**
     * 儲存提供者: sftp / s3 / r2 / gcs
     */
    @Column(length = 20)
    private String provider;

    /**
     * 儲存端點 URL
     */
    @Column(length = 500)
    private String endpoint;

    /**
     * Bucket / Container 名稱
     */
    @Column(length = 200)
    private String bucket;

    /**
     * 儲存路徑前綴
     */
    @Column(name = "base_path", length = 500)
    private String basePath;

    /**
     * Access Key (S3/R2) - 加密儲存
     */
    @Column(name = "access_key", length = 1000)
    private String accessKey;

    /**
     * Secret Key (S3/R2) - 加密儲存
     */
    @Column(name = "secret_key", length = 1000)
    private String secretKey;

    /**
     * Region (S3/R2)
     */
    @Column(length = 50)
    private String region;

    /**
     * GCS Service Account JSON - 加密儲存
     */
    @Column(name = "service_account_json", columnDefinition = "TEXT")
    private String serviceAccountJson;

    // ========== SFTP ==========

    @Column(name = "sftp_host", length = 200)
    private String sftpHost;

    @Column(name = "sftp_port")
    @Builder.Default
    private Integer sftpPort = 22;

    @Column(name = "sftp_username", length = 200)
    private String sftpUsername;

    /**
     * SFTP 密碼 - 加密儲存
     */
    @Column(name = "sftp_password", length = 1000)
    private String sftpPassword;

    /**
     * SFTP Private Key - 加密儲存
     */
    @Column(name = "sftp_private_key", columnDefinition = "TEXT")
    private String sftpPrivateKey;

    @Column(name = "sftp_path", length = 500)
    private String sftpPath;

    // ========== 排程 ==========

    /**
     * 自動備份 Cron 表達式 (null = 手動)
     */
    @Column(length = 50)
    private String schedule;

    @Column(name = "last_backup_at")
    private Instant lastBackupAt;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
