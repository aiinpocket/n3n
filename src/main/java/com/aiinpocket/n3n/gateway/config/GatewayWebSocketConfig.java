package com.aiinpocket.n3n.gateway.config;

import com.aiinpocket.n3n.gateway.handler.GatewayWebSocketHandler;
import com.aiinpocket.n3n.gateway.handler.SecureGatewayWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for the Gateway protocol.
 * Provides two endpoints:
 * - /ws/agent - Legacy unencrypted mode (deprecated)
 * - /ws/agent/secure - End-to-end encrypted mode (recommended)
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class GatewayWebSocketConfig implements WebSocketConfigurer {

    private final GatewayWebSocketHandler gatewayWebSocketHandler;
    private final SecureGatewayWebSocketHandler secureGatewayWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Legacy unencrypted endpoint (deprecated, for backwards compatibility)
        registry.addHandler(gatewayWebSocketHandler, "/ws/agent")
            .setAllowedOrigins("*");

        // Secure encrypted endpoint (recommended)
        registry.addHandler(secureGatewayWebSocketHandler, "/ws/agent/secure")
            .setAllowedOrigins("*");
    }
}
