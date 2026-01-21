package com.aiinpocket.n3n.credential.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Data Encryption Key (DEK) - 資料加密金鑰
 *
 * 用於 Envelope Encryption 架構：
 * - DEK 由 Master Key 加密後存儲
 * - DEK 用於加密實際敏感資料
 * - 支援金鑰輪換而不需重新加密所有資料
 */
@Entity
@Table(name = "data_encryption_keys")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataEncryptionKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * 金鑰版本號（遞增）
     */
    @Column(name = "key_version", nullable = false)
    private Integer keyVersion;

    /**
     * 金鑰用途：CREDENTIAL, LOG, GENERAL
     */
    @Column(nullable = false)
    @Builder.Default
    private String purpose = "CREDENTIAL";

    /**
     * 加密後的 DEK（由 Master Key 加密）
     */
    @Column(name = "encrypted_key", nullable = false)
    private byte[] encryptedKey;

    /**
     * 加密 DEK 時使用的 IV
     */
    @Column(name = "encryption_iv", nullable = false)
    private byte[] encryptionIv;

    /**
     * 金鑰狀態：ACTIVE, DECRYPT_ONLY, RETIRED
     * - ACTIVE: 用於加密和解密
     * - DECRYPT_ONLY: 僅用於解密舊資料
     * - RETIRED: 已廢棄，不再使用
     */
    @Column(nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    /**
     * 所屬工作區（null 表示全域）
     */
    @Column(name = "workspace_id")
    private UUID workspaceId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /**
     * 金鑰輪換時間
     */
    @Column(name = "rotated_at")
    private Instant rotatedAt;

    /**
     * 金鑰過期時間（建議定期輪換）
     */
    @Column(name = "expires_at")
    private Instant expiresAt;
}
