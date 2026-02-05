package com.aiinpocket.n3n.gateway.config;

import com.aiinpocket.n3n.gateway.handler.GatewayWebSocketHandler;
import com.aiinpocket.n3n.gateway.handler.SecureGatewayWebSocketHandler;
import com.aiinpocket.n3n.gateway.security.DeviceTokenHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * Security features:
 * - Device token authentication via DeviceTokenHandshakeInterceptor
 * - Configurable allowed origins (default: localhost only for development)
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
    private final DeviceTokenHandshakeInterceptor deviceTokenHandshakeInterceptor;

    @Value("${n3n.gateway.allowed-origins:${app.allowed-origins:http://localhost:3000,http://localhost:8080}}")
    private String allowedOriginsConfig;

    @Value("${n3n.gateway.require-authentication:true}")
    private boolean requireAuthentication;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        log.info("Registering Gateway WebSocket handlers...");

        String[] allowedOrigins = parseAllowedOrigins();
        log.info("Gateway WebSocket allowed origins: {}", String.join(", ", allowedOrigins));
        log.info("Gateway WebSocket authentication required: {}", requireAuthentication);

        // Legacy unencrypted endpoint (deprecated, for backwards compatibility)
        var legacyHandler = registry.addHandler(gatewayWebSocketHandler, "/gateway/agent")
            .setAllowedOrigins(allowedOrigins);

        if (requireAuthentication) {
            legacyHandler.addInterceptors(deviceTokenHandshakeInterceptor);
        }
        log.info("Registered /gateway/agent endpoint (authenticated: {})", requireAuthentication);

        // Secure encrypted endpoint (recommended)
        var secureHandler = registry.addHandler(secureGatewayWebSocketHandler, "/gateway/agent/secure")
            .setAllowedOrigins(allowedOrigins);

        if (requireAuthentication) {
            secureHandler.addInterceptors(deviceTokenHandshakeInterceptor);
        }
        log.info("Registered /gateway/agent/secure endpoint (authenticated: {})", requireAuthentication);
    }

    /**
     * Parse allowed origins from configuration
     * Supports comma-separated list
     */
    private String[] parseAllowedOrigins() {
        if (allowedOriginsConfig == null || allowedOriginsConfig.isBlank()) {
            log.warn("No allowed origins configured, defaulting to localhost only");
            return new String[]{"http://localhost:3000", "http://localhost:8080"};
        }

        return allowedOriginsConfig.split(",");
    }
}
