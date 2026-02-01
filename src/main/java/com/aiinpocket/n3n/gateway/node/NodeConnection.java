package com.aiinpocket.n3n.gateway.node;

import lombok.Builder;
import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an active connection from a local agent to the gateway.
 */
@Data
@Builder
public class NodeConnection {

    /**
     * Unique connection ID
     */
    private String connectionId;

    /**
     * User ID this agent belongs to
     */
    private UUID userId;

    /**
     * Device information
     */
    private DeviceInfo device;

    /**
     * List of capabilities this node provides
     */
    private List<String> capabilities;

    /**
     * Current permissions granted to this node
     */
    private NodePermissions permissions;

    /**
     * Device token for reconnection
     */
    private String deviceToken;

    /**
     * WebSocket session
     */
    private transient WebSocketSession session;

    /**
     * Connection status
     */
    private ConnectionStatus status;

    /**
     * When this connection was established
     */
    private Instant connectedAt;

    /**
     * Last activity timestamp
     */
    private Instant lastActiveAt;

    /**
     * Ping/latency in milliseconds
     */
    private long latencyMs;

    @Data
    @Builder
    public static class DeviceInfo {
        /**
         * Stable device fingerprint
         */
        private String id;

        /**
         * User-friendly device name
         */
        private String displayName;

        /**
         * Agent version
         */
        private String version;

        /**
         * Platform: macos, windows, linux, ios, android
         */
        private String platform;

        /**
         * Architecture: x64, arm64
         */
        private String arch;

        /**
         * Unique per-session instance ID
         */
        private String instanceId;
    }

    @Data
    @Builder
    public static class NodePermissions {
        // macOS TCC permissions
        private boolean accessibility;
        private boolean screenRecording;
        private boolean fullDiskAccess;
        private boolean camera;
        private boolean microphone;

        // Application-level permissions
        private List<String> allowedCommands;
        private List<String> blockedCommands;
        private List<String> allowedPaths;
        private List<String> blockedPaths;
    }

    public enum ConnectionStatus {
        CONNECTING,
        AUTHENTICATING,
        CONNECTED,
        DISCONNECTING,
        DISCONNECTED
    }

    /**
     * Update last activity timestamp
     */
    public void touch() {
        this.lastActiveAt = Instant.now();
    }

    /**
     * Check if this node has a specific capability
     */
    public boolean hasCapability(String capability) {
        return capabilities != null && capabilities.contains(capability);
    }

    /**
     * Check if the session is still active
     */
    public boolean isActive() {
        return session != null && session.isOpen() && status == ConnectionStatus.CONNECTED;
    }
}
