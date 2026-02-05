package com.aiinpocket.n3n.gateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Device Token Handshake Interceptor for Agent WebSocket connections
 *
 * 在 WebSocket handshake 階段驗證 device token，確保只有已配對的裝置可以建立連線。
 * Token 可以透過以下方式提供：
 * 1. Query parameter: ?token=xxx
 * 2. X-Device-Token header
 * 3. Sec-WebSocket-Protocol header (格式: "n3n-device, <token>")
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeviceTokenHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTR = "userId";
    public static final String DEVICE_ID_ATTR = "deviceId";
    public static final String DEVICE_TOKEN_ATTR = "deviceToken";

    private final AgentPairingService agentPairingService;
    private final DeviceKeyStore deviceKeyStore;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        String token = extractToken(request);

        if (token == null || token.isBlank()) {
            log.warn("Gateway WebSocket handshake rejected: No device token provided from {}",
                    request.getRemoteAddress());
            return false;
        }

        try {
            // 驗證 device token 並取得 userId
            Optional<UUID> userIdOpt = agentPairingService.validateDeviceToken(token);

            if (userIdOpt.isEmpty()) {
                log.warn("Gateway WebSocket handshake rejected: Invalid device token from {}",
                        request.getRemoteAddress());
                return false;
            }

            UUID userId = userIdOpt.get();

            // 從 token 中取得 deviceId
            String deviceId = extractDeviceIdFromToken(token);
            if (deviceId == null) {
                log.warn("Gateway WebSocket handshake rejected: Could not extract deviceId from token");
                return false;
            }

            // 驗證裝置存在且未被撤銷
            Optional<DeviceKeyStore.DeviceKey> deviceKeyOpt = deviceKeyStore.getDeviceKey(deviceId);
            if (deviceKeyOpt.isEmpty() || deviceKeyOpt.get().isRevoked()) {
                log.warn("Gateway WebSocket handshake rejected: Device not found or revoked: {}", deviceId);
                return false;
            }

            // 將資訊存入 WebSocket session attributes
            attributes.put(USER_ID_ATTR, userId);
            attributes.put(DEVICE_ID_ATTR, deviceId);
            attributes.put(DEVICE_TOKEN_ATTR, token);

            log.debug("Gateway WebSocket handshake accepted for device: {} (user: {})", deviceId, userId);
            return true;

        } catch (Exception e) {
            log.warn("Gateway WebSocket handshake rejected: Token validation failed - {}",
                    e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        // No-op
    }

    /**
     * 從請求中擷取 device token
     *
     * 優先順序：
     * 1. Query parameter 'token'
     * 2. X-Device-Token header
     * 3. Sec-WebSocket-Protocol header
     */
    private String extractToken(ServerHttpRequest request) {
        // 1. Try query parameter
        String query = request.getURI().getQuery();
        if (query != null) {
            String token = extractFromQuery(query, "token");
            if (token != null) {
                return token;
            }
        }

        // 2. Try X-Device-Token header
        String deviceTokenHeader = request.getHeaders().getFirst("X-Device-Token");
        if (deviceTokenHeader != null && !deviceTokenHeader.isBlank()) {
            return deviceTokenHeader;
        }

        // 3. Try Sec-WebSocket-Protocol header (for clients that can't set custom headers)
        // Format: "n3n-device, <token>"
        String protocolHeader = request.getHeaders().getFirst("Sec-WebSocket-Protocol");
        if (protocolHeader != null && protocolHeader.contains("n3n-device")) {
            String[] parts = protocolHeader.split(",");
            if (parts.length >= 2) {
                return parts[1].trim();
            }
        }

        return null;
    }

    private String extractFromQuery(String query, String paramName) {
        for (String param : query.split("&")) {
            String[] keyValue = param.split("=", 2);
            if (keyValue.length == 2 && paramName.equals(keyValue[0])) {
                return keyValue[1];
            }
        }
        return null;
    }

    /**
     * 從 device token 中擷取 deviceId
     * Token format: base64(userId:deviceId:timestamp:signature)
     */
    private String extractDeviceIdFromToken(String token) {
        try {
            String decoded = new String(java.util.Base64.getDecoder().decode(token));
            String[] parts = decoded.split(":");
            if (parts.length >= 2) {
                return parts[1];
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
