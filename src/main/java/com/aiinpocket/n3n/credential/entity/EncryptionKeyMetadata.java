package com.aiinpocket.n3n.credential.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * 加密金鑰元資料（不儲存實際金鑰）
 */
@Entity
@Table(name = "encryption_key_metadata")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptionKeyMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 金鑰類型：recovery, master, instance_salt
     */
    @Column(name = "key_type", nullable = false)
    private String keyType;

    /**
     * 金鑰版本
     */
    @Column(name = "key_version", nullable = false)
    @Builder.Default
    private Integer keyVersion = 1;

    /**
     * Recovery Key 的 SHA-256 hash（用於驗證）
     */
    @Column(name = "key_hash")
    private String keyHash;

    /**
     * 金鑰來源：auto_generated, user_provided, environment
     */
    @Column(nullable = false)
    private String source;

    /**
     * 金鑰狀態：active, deprecated, compromised
     */
    @Column(nullable = false)
    @Builder.Default
    private String status = "active";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    // 常量定義
    public static final String TYPE_RECOVERY = "recovery";
    public static final String TYPE_MASTER = "master";
    public static final String TYPE_INSTANCE_SALT = "instance_salt";

    public static final String SOURCE_AUTO_GENERATED = "auto_generated";
    public static final String SOURCE_USER_PROVIDED = "user_provided";
    public static final String SOURCE_ENVIRONMENT = "environment";

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DEPRECATED = "deprecated";
    public static final String STATUS_COMPROMISED = "compromised";
}
