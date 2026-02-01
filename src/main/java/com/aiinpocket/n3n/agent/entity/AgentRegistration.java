package com.aiinpocket.n3n.agent.entity;

import com.aiinpocket.n3n.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Agent 註冊記錄
 * 追蹤 Agent 的一次性 Token 註冊狀態和封鎖狀態
 */
@Entity
@Table(name = "agent_registrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * 註冊 Token 的 SHA256 雜湊值
     * 實際 Token 只在產生時回傳一次，不儲存明文
     */
    @Column(name = "registration_token_hash", nullable = false, length = 64)
    private String registrationTokenHash;

    /**
     * 註冊成功後分配的 Device ID
     */
    @Column(name = "device_id", length = 64)
    private String deviceId;

    /**
     * 裝置名稱
     */
    @Column(name = "device_name")
    private String deviceName;

    /**
     * 平台：macos, windows, linux
     */
    @Column(length = 50)
    private String platform;

    /**
     * 裝置指紋
     */
    @Column
    private String fingerprint;

    /**
     * 註冊狀態
     */
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AgentStatus status = AgentStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * 註冊完成時間
     */
    @Column(name = "registered_at")
    private Instant registeredAt;

    /**
     * 封鎖時間
     */
    @Column(name = "blocked_at")
    private Instant blockedAt;

    /**
     * 封鎖原因
     */
    @Column(name = "blocked_reason", columnDefinition = "TEXT")
    private String blockedReason;

    /**
     * 最後活動時間
     */
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    /**
     * 標記為已註冊
     */
    public void markRegistered(String deviceId, String deviceName, String platform, String fingerprint) {
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.platform = platform;
        this.fingerprint = fingerprint;
        this.status = AgentStatus.REGISTERED;
        this.registeredAt = Instant.now();
        this.lastSeenAt = Instant.now();
    }

    /**
     * 封鎖此 Agent
     */
    public void block(String reason) {
        this.status = AgentStatus.BLOCKED;
        this.blockedAt = Instant.now();
        this.blockedReason = reason;
    }

    /**
     * 解除封鎖
     */
    public void unblock() {
        this.status = AgentStatus.REGISTERED;
        this.blockedAt = null;
        this.blockedReason = null;
    }

    /**
     * 更新最後活動時間
     */
    public void updateLastSeen() {
        this.lastSeenAt = Instant.now();
    }

    /**
     * Agent 狀態
     */
    public enum AgentStatus {
        /**
         * 待註冊 - Token 已產生但尚未使用
         */
        PENDING,

        /**
         * 已註冊 - Agent 已成功註冊並可連線
         */
        REGISTERED,

        /**
         * 已封鎖 - Agent 被管理員封鎖，連線將被拒絕
         */
        BLOCKED
    }
}
