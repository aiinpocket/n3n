package com.aiinpocket.n3n.hostedapp.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hosted App（沙盒動態應用）本體：使用者上傳 zip（compose / Dockerfile），
 * 平台解析出參數表單，填參後以強化限制的 Docker 容器部署。
 *
 * manifest 為 AppManifest 的 JSON 形式；params 為使用者填寫的參數值
 * （秘密類參數以 enc:v1: 前綴標記、AES-256-GCM 加密存放）。
 */
@Entity
@Table(name = "hosted_apps", indexes = {
        @Index(name = "idx_hosted_apps_owner", columnList = "owner_id"),
        @Index(name = "idx_hosted_apps_slug", columnList = "slug")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HostedApp {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    /** compose | dockerfile */
    @Column(name = "app_type", nullable = false, length = 16)
    private String appType;

    /** created | deploying | running | stopped | failed */
    @Builder.Default
    @Column(nullable = false, length = 16)
    private String status = AppStatus.CREATED;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> manifest;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> params;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "container_ids", columnDefinition = "jsonb")
    private List<String> containerIds;

    @Column(name = "host_port")
    private Integer hostPort;

    @Column(name = "internal_port")
    private Integer internalPort;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    /** 原始 zip（重新部署 / 建置映像用），大小由 n3n.apps.max-zip-mb 限制 */
    @ToString.Exclude
    @Column(name = "zip_data")
    private byte[] zipData;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
