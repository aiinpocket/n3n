package com.aiinpocket.n3n.agent.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Gateway 設定
 * 單例表格，只有一筆記錄
 */
@Entity
@Table(name = "gateway_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GatewaySettings {

    @Id
    @Builder.Default
    private Long id = 1L;

    /**
     * Gateway 網域或 IP
     * Agent 將使用此網域連線到 Server
     */
    @Column(name = "gateway_domain", nullable = false)
    @Builder.Default
    private String gatewayDomain = "localhost";

    /**
     * Gateway 連接埠
     */
    @Column(name = "gateway_port", nullable = false)
    @Builder.Default
    private Integer gatewayPort = 9443;

    /**
     * 是否啟用 Gateway
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * 取得完整的 WebSocket URL
     */
    public String getWebSocketUrl() {
        String protocol = gatewayPort == 443 || gatewayPort == 9443 ? "wss" : "ws";
        return String.format("%s://%s:%d/gateway/agent/secure", protocol, gatewayDomain, gatewayPort);
    }

    /**
     * 取得 HTTP URL (用於註冊)
     */
    public String getHttpUrl() {
        String protocol = gatewayPort == 443 || gatewayPort == 9443 ? "https" : "http";
        return String.format("%s://%s:%d", protocol, gatewayDomain, gatewayPort);
    }
}
