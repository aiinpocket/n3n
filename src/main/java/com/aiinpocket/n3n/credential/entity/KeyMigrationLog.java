package com.aiinpocket.n3n.credential.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 金鑰遷移記錄
 */
@Entity
@Table(name = "key_migration_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyMigrationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 原始 key 版本
     */
    @Column(name = "from_version", nullable = false)
    private Integer fromVersion;

    /**
     * 目標 key 版本
     */
    @Column(name = "to_version", nullable = false)
    private Integer toVersion;

    /**
     * 遷移的憑證 ID
     */
    @Column(name = "credential_id")
    private UUID credentialId;

    /**
     * 執行遷移的使用者
     */
    @Column(name = "migrated_by")
    private UUID migratedBy;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /**
     * 遷移狀態：in_progress, completed, failed
     */
    @Column(nullable = false)
    @Builder.Default
    private String status = "in_progress";

    @Column(name = "error_message")
    private String errorMessage;

    // 常量定義
    public static final String STATUS_IN_PROGRESS = "in_progress";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_FAILED = "failed";

    /**
     * 標記遷移完成
     */
    public void markCompleted() {
        this.status = STATUS_COMPLETED;
        this.completedAt = Instant.now();
    }

    /**
     * 標記遷移失敗
     */
    public void markFailed(String errorMessage) {
        this.status = STATUS_FAILED;
        this.completedAt = Instant.now();
        this.errorMessage = errorMessage;
    }
}
