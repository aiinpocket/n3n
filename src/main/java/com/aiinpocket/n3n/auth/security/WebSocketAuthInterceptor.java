package com.aiinpocket.n3n.auth.security;

import com.aiinpocket.n3n.auth.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

/**
 * WebSocket Authentication Interceptor for STOMP endpoints
 *
 * 在 WebSocket handshake 階段驗證 JWT token，確保只有經過認證的連線可以建立。
 *
 * Security:
 * - 預設要求認證 (require-authentication=true)
 * - 可透過配置允許未認證連線（僅供開發環境使用）
 * - Token 可透過 Authorization header、Sec-WebSocket-Protocol header 或 query parameter 提供
 * - SockJS 不支援自訂 HTTP headers，因此 query parameter 是 SockJS fallback 的必要方式
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    @Value("${n3n.websocket.require-authentication:true}")
    private boolean requireAuthentication;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            String token = null;

            // Try Authorization header first (preferred)
            String authHeader = servletRequest.getServletRequest().getHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
            }

            // Fallback: Sec-WebSocket-Protocol header (for browsers that can't set Auth header)
            if (token == null || token.isEmpty()) {
                String wsProtocol = servletRequest.getServletRequest().getHeader("Sec-WebSocket-Protocol");
                if (wsProtocol != null && wsProtocol.startsWith("access_token,")) {
                    token = wsProtocol.substring("access_token,".length()).trim();
                }
            }

            // Fallback: query parameter (required for SockJS which cannot set custom headers during handshake)
            if (token == null || token.isEmpty()) {
                token = servletRequest.getServletRequest().getParameter("token");
            }

            if (token != null && !token.isEmpty()) {
                try {
                    if (jwtService.validateToken(token)) {
                        UUID userId = jwtService.extractUserId(token);
                        String email = jwtService.extractEmail(token);
                        attributes.put("userId", userId);
                        attributes.put("email", email);
                        attributes.put("authenticated", true);
                        log.debug("WebSocket handshake authenticated for user: {}", email);
                        return true;
                    } else {
                        log.warn("WebSocket handshake rejected: Invalid JWT token from {}",
                                request.getRemoteAddress());
                        return false;
                    }
                } catch (Exception e) {
                    log.warn("WebSocket authentication failed: {}", e.getMessage());
                    return false;
                }
            }

            // No token provided
            if (requireAuthentication) {
                log.warn("WebSocket handshake rejected: No authentication token provided from {}",
                        request.getRemoteAddress());
                return false;
            }

            // Allow unauthenticated connection only in development mode
            log.warn("WebSocket connection allowed without authentication (development mode)");
            attributes.put("authenticated", false);
            return true;
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // No action needed after handshake
    }
}
