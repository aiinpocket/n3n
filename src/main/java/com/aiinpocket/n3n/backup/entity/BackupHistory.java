package com.aiinpocket.n3n.backup.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 備份歷史記錄
 */
@Entity
@Table(name = "backup_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackupHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String filename;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(nullable = false, length = 20)
    private String provider;

    /**
     * SHA-256 checksum
     */
    @Column(length = 100)
    private String checksum;

    /**
     * completed / failed
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "completed";

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "triggered_by")
    private UUID triggeredBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public void markFailed(String errorMessage) {
        this.status = "failed";
        this.errorMessage = errorMessage;
    }
}
