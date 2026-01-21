package com.aiinpocket.n3n.common.config;

import com.aiinpocket.n3n.auth.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            // Try to get token from query parameter
            String token = servletRequest.getServletRequest().getParameter("token");

            // If not in query param, try Authorization header
            if (token == null || token.isEmpty()) {
                String authHeader = servletRequest.getServletRequest().getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    token = authHeader.substring(7);
                }
            }

            if (token != null && !token.isEmpty()) {
                try {
                    if (jwtService.validateToken(token)) {
                        UUID userId = jwtService.extractUserId(token);
                        String email = jwtService.extractEmail(token);
                        attributes.put("userId", userId);
                        attributes.put("email", email);
                        log.debug("WebSocket handshake authenticated for user: {}", email);
                        return true;
                    }
                } catch (Exception e) {
                    log.warn("WebSocket authentication failed: {}", e.getMessage());
                }
            }

            // Allow connection but mark as unauthenticated
            // This allows public execution monitoring if needed
            log.debug("WebSocket connection without authentication");
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
