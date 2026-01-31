package com.aiinpocket.n3n.flow.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 元件自動安裝記錄
 */
@Entity
@Table(name = "component_installations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComponentInstallation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 關聯的匯入記錄 ID
     */
    @Column(name = "import_id")
    private UUID importId;

    /**
     * 元件名稱
     */
    @Column(name = "component_name", nullable = false)
    private String componentName;

    /**
     * 元件版本
     */
    @Column(nullable = false)
    private String version;

    /**
     * Docker image URI
     */
    @Column(nullable = false)
    private String image;

    /**
     * Docker registry URL (e.g., docker.io, ghcr.io)
     */
    @Column(name = "registry_url")
    private String registryUrl;

    /**
     * 安裝狀態：pending, pulling, installed, failed
     */
    @Column(nullable = false)
    @Builder.Default
    private String status = "pending";

    /**
     * 錯誤訊息
     */
    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // 常量定義
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PULLING = "pulling";
    public static final String STATUS_INSTALLED = "installed";
    public static final String STATUS_FAILED = "failed";

    /**
     * 標記開始拉取
     */
    public void markPulling() {
        this.status = STATUS_PULLING;
    }

    /**
     * 標記安裝完成
     */
    public void markInstalled() {
        this.status = STATUS_INSTALLED;
        this.completedAt = Instant.now();
    }

    /**
     * 標記安裝失敗
     */
    public void markFailed(String errorMessage) {
        this.status = STATUS_FAILED;
        this.completedAt = Instant.now();
        this.errorMessage = errorMessage;
    }
}
