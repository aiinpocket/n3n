package com.aiinpocket.n3n.site.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 網站的單一檔案：path 為站內相對路徑（已通過 SitePaths 淨化），
 * 內容以 bytea 存於資料庫，適合小型靜態網站（大小上限由 SiteService 強制）。
 */
@Entity
@Table(name = "site_files",
        indexes = @Index(name = "idx_site_files_site", columnList = "site_id"),
        uniqueConstraints = @UniqueConstraint(name = "uq_site_files_site_path",
                columnNames = {"site_id", "path"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteFile {

    @Id
    private UUID id;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(nullable = false, length = 400)
    private String path;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(nullable = false)
    private byte[] data;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
