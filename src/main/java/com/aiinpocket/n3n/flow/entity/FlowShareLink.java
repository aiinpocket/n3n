package com.aiinpocket.n3n.flow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * 流程分享連結實體
 *
 * 持有連結 token 的登入使用者可換取對應權限的流程分享（FlowShare）。
 * 連結權限僅限 view / edit（不開放 admin）。
 */
@Entity
@Table(name = "flow_share_links")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowShareLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flow_id", nullable = false)
    private UUID flowId;

    /**
     * 分享連結 token（base64url，無 padding）
     */
    @Column(nullable = false, unique = true, length = 64)
    private String token;

    /**
     * 連結授予的權限：view 或 edit
     */
    @Column(nullable = false, length = 16)
    @Builder.Default
    private String permission = "view";

    /**
     * 建立者 ID
     */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at")
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * 到期時間（null 表示永久有效）
     */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /**
     * 撤銷時間（null 表示仍有效）
     */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * 連結是否仍可使用（未撤銷且未到期）
     */
    public boolean isActive() {
        if (revokedAt != null) {
            return false;
        }
        return expiresAt == null || expiresAt.isAfter(Instant.now());
    }
}
