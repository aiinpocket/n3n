package com.aiinpocket.n3n.gateway.config;

import com.aiinpocket.n3n.gateway.handler.GatewayWebSocketHandler;
import com.aiinpocket.n3n.gateway.handler.SecureGatewayWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket configuration for the Gateway protocol.
 * Provides two endpoints:
 * - /gateway/agent - Legacy unencrypted mode (deprecated)
 * - /gateway/agent/secure - End-to-end encrypted mode (recommended)
 *
 * Note: These endpoints use a different base path (/gateway/) to avoid conflicts
 * with the STOMP WebSocket configuration which uses /ws/.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@Order(1) // Ensure this runs before STOMP config
@Slf4j
public class GatewayWebSocketConfig implements WebSocketConfigurer {

    private final GatewayWebSocketHandler gatewayWebSocketHandler;
    private final SecureGatewayWebSocketHandler secureGatewayWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        log.info("Registering Gateway WebSocket handlers...");

        // Legacy unencrypted endpoint (deprecated, for backwards compatibility)
        registry.addHandler(gatewayWebSocketHandler, "/gateway/agent")
            .setAllowedOrigins("*");
        log.info("Registered /gateway/agent endpoint");

        // Secure encrypted endpoint (recommended)
        registry.addHandler(secureGatewayWebSocketHandler, "/gateway/agent/secure")
            .setAllowedOrigins("*");
        log.info("Registered /gateway/agent/secure endpoint");
    }
}
