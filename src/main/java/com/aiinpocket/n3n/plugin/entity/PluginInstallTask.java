package com.aiinpocket.n3n.plugin.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Plugin 安裝任務
 * 追蹤 Plugin 安裝進度，包括 Docker 映像拉取狀態
 */
@Data
@Entity
@Table(name = "plugin_install_tasks")
public class PluginInstallTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "plugin_id")
    private UUID pluginId;

    @Column(name = "node_type", length = 100, nullable = false)
    private String nodeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20, nullable = false)
    private InstallSource source;

    @Column(name = "source_reference", length = 500)
    private String sourceReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private InstallStatus status = InstallStatus.PENDING;

    @Column(name = "progress_percent")
    private Integer progressPercent = 0;

    @Column(name = "current_stage", length = 200)
    private String currentStage;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "container_id", length = 100)
    private String containerId;

    @Column(name = "container_port")
    private Integer containerPort;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * 安裝來源
     */
    public enum InstallSource {
        MARKETPLACE,    // 從 N3N Marketplace 安裝
        DOCKER_HUB,     // 從 Docker Hub 拉取映像
        DOCKER_REGISTRY,// 從私有 Docker Registry
        LOCAL,          // 本地安裝
        GIT             // 從 Git 倉庫安裝
    }

    /**
     * 安裝狀態
     */
    public enum InstallStatus {
        PENDING,        // 等待中
        PULLING,        // 正在拉取映像
        STARTING,       // 正在啟動容器
        CONFIGURING,    // 正在配置
        REGISTERING,    // 正在註冊節點
        COMPLETED,      // 完成
        FAILED,         // 失敗
        CANCELLED       // 已取消
    }

    /**
     * 更新進度
     */
    public void updateProgress(int percent, String stage) {
        this.progressPercent = percent;
        this.currentStage = stage;
        if (percent > 0 && this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }
    }

    /**
     * 標記完成
     */
    public void markCompleted() {
        this.status = InstallStatus.COMPLETED;
        this.progressPercent = 100;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 標記失敗
     */
    public void markFailed(String errorMessage) {
        this.status = InstallStatus.FAILED;
        this.errorMessage = errorMessage;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 是否為終態
     */
    public boolean isTerminal() {
        return status == InstallStatus.COMPLETED ||
               status == InstallStatus.FAILED ||
               status == InstallStatus.CANCELLED;
    }
}
