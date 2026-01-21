package com.aiinpocket.n3n.flow.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * 流程分享實體
 *
 * 記錄流程分享給哪些用戶，以及其權限。
 */
@Entity
@Table(name = "flow_shares")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlowShare {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "flow_id", nullable = false)
    private UUID flowId;

    /**
     * 被分享的用戶 ID（已註冊用戶）
     */
    @Column(name = "user_id")
    private UUID userId;

    /**
     * 邀請的 Email（尚未註冊的用戶）
     */
    @Column(name = "invited_email")
    private String invitedEmail;

    /**
     * 權限等級：view, edit, admin
     * - view: 僅可檢視
     * - edit: 可編輯流程
     * - admin: 可管理分享設定
     */
    @Column(nullable = false)
    @Builder.Default
    private String permission = "view";

    /**
     * 分享者 ID
     */
    @Column(name = "shared_by", nullable = false)
    private UUID sharedBy;

    /**
     * 分享時間
     */
    @Column(name = "shared_at")
    @Builder.Default
    private Instant sharedAt = Instant.now();

    /**
     * 接受邀請的時間（適用於 Email 邀請）
     */
    @Column(name = "accepted_at")
    private Instant acceptedAt;
}
