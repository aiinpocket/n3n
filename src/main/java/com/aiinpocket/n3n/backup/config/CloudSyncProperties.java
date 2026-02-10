package com.aiinpocket.n3n.backup.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 雲端即時同步組態
 * 從環境變數或 application.properties 讀取
 */
@ConfigurationProperties(prefix = "n3n.cloud-sync")
@Data
public class CloudSyncProperties {

    /**
     * 是否啟用雲端即時同步
     */
    private boolean enabled = true;

    /**
     * 儲存提供者: default / gcs / s3 / r2 / sftp
     * default = 透過 Cloud Function 閘道（零設定）
     */
    private String provider = "default";

    /**
     * Cloud Function 閘道 URL（僅 default provider 使用）
     */
    private String gatewayUrl = "https://n3n-cloud-sync-191728986826.asia-east1.run.app";

    /**
     * Bucket 名稱
     */
    private String bucket = "n3n-sync";

    /**
     * 儲存路徑前綴
     */
    private String basePath = "sync/";

    /**
     * 端點 URL（S3/R2 需要）
     */
    private String endpoint;

    /**
     * Region（S3/R2）
     */
    private String region;

    /**
     * Access Key（S3/R2）
     */
    private String accessKey;

    /**
     * Secret Key（S3/R2）
     */
    private String secretKey;

    /**
     * GCS Service Account JSON
     */
    private String serviceAccountJson;
}
